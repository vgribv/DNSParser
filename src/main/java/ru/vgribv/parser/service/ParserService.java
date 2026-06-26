package ru.vgribv.parser.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.microsoft.playwright.options.Proxy;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microsoft.playwright.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.vgribv.parser.config.DnsParserProperties;
import ru.vgribv.parser.dto.DiscountResultDto;
import ru.vgribv.parser.dto.ProductHtmlDto;
import ru.vgribv.parser.entity.ArchivedProduct;
import ru.vgribv.parser.entity.PriceHistory;
import ru.vgribv.parser.event.ParsingResultEvent;
import ru.vgribv.parser.entity.Category;
import ru.vgribv.parser.entity.Product;
import ru.vgribv.parser.repository.ArchivedProductRepository;
import ru.vgribv.parser.repository.CategoryRepository;
import ru.vgribv.parser.repository.PriceHistoryRepository;
import ru.vgribv.parser.repository.ProductRepository;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.BrowserType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ParserService {
    private final ParserService self;
    @Getter
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ApplicationEventPublisher publisher;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ArchivedProductRepository archivedProductRepository;
    private final FileWriteService fileWriteService;
    private final SendToUserService sendToUserService;
    private final PriceHistoryRepository priceHistoryRepository;
    private Playwright playwright;
    private BrowserContext context;
    private final DnsParserProperties properties;

    public ParserService(@Lazy ParserService self, ApplicationEventPublisher publisher,
                         ProductRepository productRepository, CategoryRepository categoryRepository,
                         ArchivedProductRepository archivedProductRepository,
                         FileWriteService fileWriteService, @Lazy SendToUserService sendToUserService,
                         PriceHistoryRepository priceHistoryRepository, DnsParserProperties properties) {
        this.self = self;
        this.publisher = publisher;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.archivedProductRepository = archivedProductRepository;
        this.fileWriteService = fileWriteService;
        this.sendToUserService = sendToUserService;
        this.priceHistoryRepository = priceHistoryRepository;
        this.properties = properties;
    }

    private void initBrowser() {
        this.playwright = Playwright.create();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        var options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(false)
                .setIgnoreDefaultArgs(List.of("--enable-automation"))
                .setArgs(Arrays.asList(
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-blink-features=AutomationControlled",
                        "--disable-ipv6",
                        "--disable-dev-shm-usage"
                ))
                .setViewportSize(1280, 720);

        String host = properties.getProxy().getHost();
        int port = properties.getProxy().getPort();

        if (port > 0 && host != null && !host.isEmpty() && !host.equals("none")) {
            options.setProxy(new Proxy("http://" + host + ":" + port));
        }

        if (!isWindows) {
            options.setExecutablePath(Paths.get(properties.getBrowserPath()));
        }

        this.context = this.playwright.chromium().launchPersistentContext(Paths.get(properties.getRealProfilePath()), options);
    }

    private void closeBrowser() {
        if (this.context != null) {
            context.close();
        }
        if (this.playwright != null) {
            playwright.close();
        }
    }

    @PreDestroy
    public void cleanup() {
        closeBrowser();
    }

    @Scheduled(cron = "${dns.cron}")
    public void scheduledRun() {
        if (running.compareAndSet(false, true)) {
            self.executeAsync();
        }
    }

    public Optional<Boolean> manualRun() {
        if (running.compareAndSet(false, true)) {
            self.executeAsync();
            return Optional.of(true);
        }
        return Optional.empty();
    }

    @Async
    public void executeAsync() {
        try {
            parseGoods();
            publisher.publishEvent(new ParsingResultEvent(true, "✅ Парсинг успешно завершен."));
        } catch (Exception e) {
            publisher.publishEvent(new ParsingResultEvent(false, "❌ Парсинг завершен с ошибкой."));
            throw new RuntimeException(e);
        } finally {
            running.set(false);
        }
    }

    private void parseGoods() {

        try {
            Files.createDirectories(Paths.get("data"));
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать директорию \"data\"", e);
        }
        LocalDateTime time = LocalDateTime.now();

        try {
            log.info("Этап 1: Запуск браузера...");
            initBrowser();

            log.info("Этап 2: Загрузка главной страницы...");
            Page page = context.pages().getFirst();

            String prefix = properties.getLink().getPrefix();

            try {
                self.navigateToPage(page, prefix + "?p=1");
            } catch (Exception e) {
                log.error("Все попытки загрузки провалены: {}", e.getMessage());
                throw new RuntimeException("Финальный провал после retry", e);
            }

            String cityPage = page.locator(".city-select__text_90n").innerText();
            log.info("!!! Загрузился город: {} !!!", cityPage);
            String city = properties.getCity();
            if (!cityPage.toLowerCase().contains(city.toLowerCase())) {
                setCity(page, city);
            }

            APIRequestContext request = context.request();
            String csrfToken = getToken(page);
            String pageReferer = page.url();
            String ajaxReferer = buildAjaxReferer(prefix);

            APIResponse responseGetCategories;
            try {
                responseGetCategories = self.getResponse(request, properties.getLink().getProductsFilters() + "?p=1", RequestOptions.create()
                        .setHeader("X-Requested-With", "XMLHttpRequest")
                        .setHeader("X-CSRF-Token", csrfToken)
                        .setHeader("Referer", pageReferer));
            } catch (RuntimeException e) {
                log.error("🛑 КРИТИЧЕСКИЙ СБОЙ: категории не получены!");
                throw new RuntimeException("DNS не ответил после всех попыток.");
            }

            String contentType = responseGetCategories.headers().get("content-type");
            if (responseGetCategories.status() != 200 || contentType == null || !contentType.contains("application/json")) {
                log.error("⚠️ DNS вернул ошибку или HTML вместо данных. Статус: {}. Тип контента: {}", responseGetCategories.status(), contentType);
                throw new RuntimeException("Неожиданный тип контента. Возможно, сработал анти-фрод");
            }

            String body = responseGetCategories.text();
            if (body == null || body.isBlank() || !body.trim().startsWith("{")) {
                String snippet = (body != null && body.length() > 200) ? body.substring(0, 200) : body;
                log.error("⚠️ Получен некорректный ответ от DNS (не JSON). Фрагмент ответа: \n{}", snippet);
                throw new RuntimeException("⚠️ Получен некорректный формат данных (вероятно, капча)");
            }

            JsonObject root3 = JsonParser.parseString(body).getAsJsonObject();
            JsonArray states3 = root3.getAsJsonObject("data")
                    .getAsJsonObject("blocks")
                    .getAsJsonArray("left").get(1).getAsJsonObject()
                    .getAsJsonArray("variants");

            Iterable<Category> allCategories = categoryRepository.findAll();
            Map<String, Category> categoryMap = new HashMap<>();
            Map<String, Category> uniqueCategoryMap = new HashMap<>();
            for (Category category : allCategories) {
                categoryMap.put(category.getCategoryId(), category);
            }

            Iterable<Product> allProducts = productRepository.findAllWithCategories();
            Map<String, Product> productMap = new HashMap<>();
            Map<String, Integer> oldPriceMap = new HashMap<>();
            for (Product product : allProducts) {
                productMap.put(product.getLinkId(), product);
                oldPriceMap.put(product.getLinkId(), product.getDiscountPrice());
            }
            List<Product> productsToSave = new ArrayList<>();
            Random random = new Random();

            log.info("Этап 3: Сбор информации о товарах...");

            for (JsonElement element1 : states3) {
                JsonObject item1 = element1.getAsJsonObject();
                JsonObject itemData1 = item1.getAsJsonObject();
                String categoryId = itemData1.get("id").getAsString();
                Category category;
                if (categoryMap.containsKey(categoryId)) {
                    category = categoryMap.get(categoryId);
                } else if (uniqueCategoryMap.containsKey(categoryId)) {
                    category = uniqueCategoryMap.get(categoryId);
                } else {
                    category = new Category(categoryId, itemData1.get("label").getAsString());
                    uniqueCategoryMap.put(categoryId, category);
                }

                int currentPage = 1;
                boolean flag = false;
                while (!flag && currentPage < 50) {
                    String rawText;
                    APIResponse responseGetPage;
                    try {
                        responseGetPage = self.getResponse(request, prefix + "?category=" + category.getCategoryId() + "&p=" + currentPage, RequestOptions.create()
                                .setHeader("X-Requested-With", "XMLHttpRequest")
                                .setHeader("X-CSRF-Token", csrfToken)
                                .setHeader("Referer", pageReferer)
                                .setTimeout(30000));
                        rawText = responseGetPage.text();
                    } catch (RuntimeException e) {
                        log.error("🛑 КРИТИЧЕСКИЙ СБОЙ: Стр {} категории {} не получена!", currentPage, category.getName());
                        throw new RuntimeException("DNS не ответил после всех попыток.");
                    }

                    if (!rawText.trim().startsWith("{")) {
                        log.warn("❌ Вместо JSON пришло что-то странное в категории {}. Пропускаю...", category.getName());
                        log.warn("Raw body: {}", rawText.substring(0, Math.min(rawText.length(), 100)));
                        break;
                    }

                    JsonObject jsonObject = JsonParser.parseString(rawText).getAsJsonObject();
                    if (!jsonObject.has("html") || jsonObject.get("html").isJsonNull() || jsonObject.get("html").getAsString().isBlank()) {
                        log.warn("В категории '{}' (ID: {}) товары не найдены. Пропускаю...",
                                category.getName(), category.getCategoryId());
                        break;
                    }
                    String realHtml = jsonObject.get("html").getAsString();
                    Document document = Jsoup.parse(realHtml);

                    Map<String, ProductHtmlDto> productHtmlMap = parseProductHtmlDto(document);
                    if (productHtmlMap.isEmpty()) {
                        log.info("В категории {} на странице {} товаров больше нет. Выхожу.", category.getName(), currentPage);
                        break;
                    }

                    if (!hasNextPage(document)) flag = true;

                    String req = buildPriceRequest(jsonObject);

                    String responseBody;

                    showProgress(category.getName(), currentPage);
                    try {
                        APIResponse responseGetAjax = self.postResponse(request, properties.getLink().getAjaxState(), RequestOptions.create()
                                .setHeader("Content-Type", "application/x-www-form-urlencoded")
                                .setHeader("X-CSRF-Token", csrfToken)
                                .setHeader("X-Requested-With", "XMLHttpRequest")
                                .setHeader("Referer", ajaxReferer)
                                .setData("data=" + req)
                                .setTimeout(30000));
                        responseBody = responseGetAjax.text();
                    } catch (RuntimeException e) {
                        throw new RuntimeException("Парсинг дальше невозможен. Ajax выдал ошибку");
                    }

                    if (responseBody == null || responseBody.isBlank() || "null".equalsIgnoreCase(responseBody.trim())) {
                        log.warn("!!! AJAX вернул пустой/null ответ. Категория: {}, страница: {}, тело: {}", category.getName(), currentPage, responseBody);
                        break;
                    }

                    JsonElement responseElement = JsonParser.parseString(responseBody);
                    if (!responseElement.isJsonObject()) {
                        log.warn("!!! AJAX вернул не JSON-object. Категория: {}, страница: {}, тело: {}", category.getName(), currentPage, responseBody);
                        break;
                    }

                    JsonObject root = responseElement.getAsJsonObject();
                    JsonElement dataElement = root.get("data");
                    if (dataElement == null || dataElement.isJsonNull() || !dataElement.isJsonObject()) {
                        log.warn("!!! В ответе AJAX нет объекта 'data'. Категория: {}, страница: {}, тело: {}", category.getName(), currentPage, responseBody);
                        break;
                    }

                    JsonElement statesElement = dataElement.getAsJsonObject().get("states");
                    if (statesElement == null || statesElement.isJsonNull() || !statesElement.isJsonArray()) {
                        log.warn("!!! В ответе AJAX нет массива 'states'. Категория: {}, страница: {}, тело: {}", category.getName(), currentPage, responseBody);
                        break;
                    }

                    JsonArray states = statesElement.getAsJsonArray();

                    for (JsonElement element : states) {
                        JsonObject item = element.getAsJsonObject();
                        JsonObject itemData = item.getAsJsonObject("data");

                        String linkId = itemData.get("id").getAsString();
                        String name = itemData.get("name").getAsString();
                        int discountPrice = itemData.getAsJsonObject("price").get("current").getAsInt();
                        int fullPrice = 0;
                        JsonElement fullPriceJson = itemData.getAsJsonObject("price").get("previous");
                        if (fullPriceJson != null) {
                            fullPrice = fullPriceJson.getAsInt();
                        }
                        Product product;
                        if (!productMap.containsKey(linkId)) {
                            product = new Product(linkId, name, discountPrice, fullPrice, category, time);
                        } else {
                            product = productMap.get(linkId);
                            product.setName(name);
                            product.setDiscountPrice(discountPrice);
                            product.setFullPrice(fullPrice);
                            product.setCategory(category);
                            product.setUpdatedAt(time);
                        }

                        if (productHtmlMap.containsKey(linkId)) {
                            ProductHtmlDto productHtmlDto = productHtmlMap.get(linkId);
                            product.setImageUrl(productHtmlDto.getImageUrl());
                            product.setCondition(productHtmlDto.getCondition());
                            product.setAppearance(productHtmlDto.getAppearance());
                            product.setCompleteness(productHtmlDto.getCompleteness());
                            product.setTypeOfRepair(productHtmlDto.getTypeOfRepair());
                        }
                        productsToSave.add(product);
                    }
                    Thread.sleep(random.nextInt(100, 500));
                    currentPage++;
                }
            }

            log.info("Этап 4: Сохранение результатов и рассылка...");

            List<Product> newProducts = getNewProductList(productsToSave, productMap);

            DiscountResultDto result = getDiscountResult(productsToSave, oldPriceMap);
            List<Product> priceHasDecreased = result.discountedProducts();
            List<PriceHistory> priceHistory = result.historyRecords();

            List<Product> deletedProducts =
                    self.saveParsedDataAndGetDeletedProducts(time, uniqueCategoryMap, productsToSave, priceHistory);
            try {
                fileWriteService.updateAllReports(newProducts, priceHasDecreased, deletedProducts);
            } catch (Exception e) {
                log.error("Ошибка записи в файл: ", e);
            }
            try {
                sendToUserService.sendGoodsToUser(newProducts, priceHasDecreased, deletedProducts);
                log.info("Парсинг и рассылка успешно завершены");
            } catch (Exception e) {
                log.error("Парсинг завершен, но произошла ошибка при рассылке пользователям. {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Критическая системная ошибка", e);
        } finally {
            closeBrowser();
        }
    }

    @Transactional
    public List<Product> saveParsedDataAndGetDeletedProducts(LocalDateTime time,
                                                             Map<String, Category> uniqueCategoryMap,
                                                             List<Product> productsToSave,
                                                             List<PriceHistory> priceHistory) {
        if (!uniqueCategoryMap.isEmpty()) {
            categoryRepository.saveAll(uniqueCategoryMap.values());
        }
        if (!productsToSave.isEmpty()) {
            productRepository.saveAll(productsToSave);
        }
        if (!priceHistory.isEmpty()) {
            priceHistoryRepository.saveAll(priceHistory);
        }
        productRepository.flush();
        List<Product> deletedProducts = productRepository.findAllByUpdatedAtBefore(time);
        if (!deletedProducts.isEmpty()) {
            archivedProductRepository.saveAll(buildArchivedProducts(deletedProducts, LocalDateTime.now()));
            priceHistoryRepository.deleteHistoryForOldProducts(time);
            productRepository.deleteOldProductsInBatch(time);
        }
        log.info("📊 Итоги транзакции: сохранено/обновлено {} товаров, удалено {} товаров",
                productsToSave.size(), deletedProducts.size());
        return deletedProducts;
    }

    private List<ArchivedProduct> buildArchivedProducts(List<Product> deletedProducts, LocalDateTime archivedAt) {
        List<ArchivedProduct> archivedProducts = new ArrayList<>(deletedProducts.size());
        for (Product product : deletedProducts) {
            ArchivedProduct archivedProduct = new ArchivedProduct();
            archivedProduct.setLinkId(product.getLinkId());
            archivedProduct.setName(product.getName());
            archivedProduct.setDiscountPrice(product.getDiscountPrice());
            archivedProduct.setFullPrice(product.getFullPrice());
            archivedProduct.setCategory(product.getCategory());
            archivedProduct.setImageUrl(product.getImageUrl());
            archivedProduct.setArchivedAt(archivedAt);
            archivedProducts.add(archivedProduct);
        }
        return archivedProducts;
    }

    private List<Product> getNewProductList(List<Product> productsToSave, Map<String, Product> productMap) {
        List<Product> bufferNew = new ArrayList<>();
        for (Product product : productsToSave) {
            if (!productMap.containsKey(product.getLinkId())) {
                bufferNew.add(product);
            }
        }
        return bufferNew;
    }

    private DiscountResultDto getDiscountResult(List<Product> productsToSave,
                                                Map<String, Integer> oldPriceMap) {
        List<Product> bufferPriceHasDecreased = new ArrayList<>();
        List<PriceHistory> historyBuffer = new ArrayList<>();

        for (Product product : productsToSave) {
            String linkId = product.getLinkId();
            Integer oldPrice = oldPriceMap.get(linkId);
            if (oldPrice == null) continue;

            Integer newPrice = Objects.requireNonNullElse(product.getDiscountPrice(), 0);

            if (!oldPrice.equals(newPrice)) {
                historyBuffer.add(new PriceHistory(product, newPrice, LocalDateTime.now()));

                if (oldPrice > newPrice) {
                    product.setOldDiscountPrice(oldPrice);
                    bufferPriceHasDecreased.add(product);
                }
            }
        }
        return new DiscountResultDto(bufferPriceHasDecreased, historyBuffer);
    }

    private Map<String, ProductHtmlDto> parseProductHtmlDto(Document document) {
        Map<String, ProductHtmlDto> productHtmlDtoMap = new HashMap<>();
        Elements products = document.select(".catalog-product");
        for (Element product : products) {
            String condition = null;
            String appearance = null;
            String completeness = null;
            String typeOfRepair = null;
            Elements block = product.select(".catalog-product__reason-text-block");
            for (Element element : block) {
                String text = element.text();
                if (text.contains("Тип товара:")) {
                    condition = text.replace("Тип товара:", "").trim();
                    if (condition.isEmpty()) condition = null;
                } else if (text.contains("Внешний вид:")) {
                    appearance = text.replace("Внешний вид:", "").trim();
                    if (appearance.isEmpty()) appearance = null;
                } else if (text.contains("Комплектация:")) {
                    completeness = text.replace("Комплектация:", "").trim();
                    if (completeness.isEmpty()) completeness = null;
                } else if (text.contains("Тип ремонта:")) {
                    typeOfRepair = text.replace("Тип ремонта:", "").trim();
                    if (typeOfRepair.isEmpty()) typeOfRepair = null;
                }
            }
            String linkId = product.attr("data-entity");
            Element img = product.select("img").first();
            String imageUrl = (img != null) ? img.attr("data-src") : null;
            if (imageUrl != null && imageUrl.startsWith("//")) {
                imageUrl = "https:" + imageUrl;
            }
            productHtmlDtoMap.put(linkId,
                    new ProductHtmlDto(imageUrl, condition, appearance, completeness, typeOfRepair));
        }
        return productHtmlDtoMap;
    }

    private String getToken(Page page) {
        String csrfToken = (String) page.evaluate("() => document.querySelector('meta[name=\"csrf-token\"]').content");

        if (csrfToken == null || csrfToken.isEmpty())
            csrfToken = (String) page.evaluate("() => window.Config.csrf");

        return csrfToken;
    }

    private String buildPriceRequest(JsonObject jsonObject) {
        String html = jsonObject.get("html").getAsString();
        String pageHash = extractProductBuyHash(jsonObject);
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select(".catalog-product");

        JsonObject reqRoot = new JsonObject();
        reqRoot.addProperty("type", "product-buy");
        reqRoot.addProperty("hash", pageHash);
        JsonArray containers = new JsonArray();

        for (Element card : cards) {
            String asId = card.select(".additional-voblers span").attr("id");
            String guid = card.attr("data-entity");

            if (!asId.isEmpty() && !guid.isEmpty()) {
                JsonObject container = new JsonObject();
                container.addProperty("id", asId);

                JsonObject data = new JsonObject();
                data.addProperty("id", guid);
                data.addProperty("type", 4);

                JsonObject params = new JsonObject();
                params.addProperty("hideButtons", true);
                data.add("params", params);

                container.add("data", data);
                containers.add(container);
            }
        }
        reqRoot.add("containers", containers);

        return reqRoot.toString();
    }

    private String extractProductBuyHash(JsonObject jsonObject) {
        JsonObject assets = getJsonObject(jsonObject, "assets");
        JsonObject inlineJs = assets != null ? getJsonObject(assets, "inlineJs") : null;

        if (inlineJs != null) {
            for (Map.Entry<String, JsonElement> entry : inlineJs.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    String hash = extractProductBuyHashFromText(value.getAsString());
                    if (!hash.isEmpty()) {
                        return hash;
                    }
                }
            }
        }

        JsonElement htmlElement = jsonObject.get("html");
        if (htmlElement != null && !htmlElement.isJsonNull()) {
            String hash = extractProductBuyHashFromText(htmlElement.getAsString());
            if (!hash.isEmpty()) {
                return hash;
            }
        }

        log.error("КРИТИЧЕСКАЯ ОШИБКА: Hash для product-buy не найден в JSON ответа!");
        return "";
    }

    private String extractProductBuyHashFromText(String text) {
        if (text == null) {
            return "";
        }
        return PRODUCT_BUY_HASH_PATTERN.matcher(text)
                .results()
                .map(matchResult -> matchResult.group(1))
                .findFirst()
                .orElse("");
    }


    private JsonObject getJsonObject(JsonObject jsonObject, String key) {
        if (jsonObject == null) {
            return null;
        }

        JsonElement element = jsonObject.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return null;
        }

        return element.getAsJsonObject();
    }

    private boolean hasNextPage(Document doc) {
        Element nextButton = doc.selectFirst(".pagination-widget__page-link_next:not(.pagination-widget__page-link_disabled)");
        return nextButton != null;
    }

    private void showProgress(String categoryName, int currentPage) {
        log.info("⏳ Обработка категории: [{}] | Страница: {}", categoryName, currentPage);
    }

    private String buildAjaxReferer(String prefix) {
        return prefix + "no-referrer";
    }

    private void setCity(Page page, String city) {
        log.info("Пытаюсь сменить город вручную через интерфейс...");
        try {
            page.locator(".city-select__text_90n").click();
            Thread.sleep(2000);

            page.locator("button._filter_tsa9p_1")
                    .filter(new Locator.FilterOptions().setHasText(city))
                    .first()
                    .click();
            Thread.sleep(2000);
            log.info("Клик по {} выполнен!", city);

            Thread.sleep(5000);
        } catch (Exception e) {
            log.error("❌ Не удалось кликнуть по городу: {}", e.getMessage());
        }

        String finalCity = page.locator(".city-select__text_90n").innerText();
        log.info("!!! ИТОГОВЫЙ ГОРОД: {} !!!", finalCity);

        if (!finalCity.toLowerCase().contains(city.toLowerCase())) {
            throw new RuntimeException("ГОРОД НЕ СМЕНИЛСЯ! СТОП ПАРСИНГ!");
        }
    }

    @Retryable(
            retryFor = {Exception.class},
            maxAttemptsExpression = "${dns.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${dns.retry.backoff-delay:1000}")
    )
    public void navigateToPage(Page page, String url) {
        log.info("Пытаюсь загрузить страницу...");
        page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(60000));
        page.waitForSelector(".product-buy__price");
    }


    @Retryable(
            retryFor = {Exception.class},
            maxAttemptsExpression = "${dns.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${dns.retry.backoff-delay:1000}")
    )
    public APIResponse getResponse(APIRequestContext request, String url, RequestOptions requestOptions) {
        APIResponse response = request.get(url, requestOptions);
        if (response.status() != 200) {
            log.warn("DNS вернул {}. Запускаю ретрай...", response.status());
            throw new RuntimeException("DNS error: " + response.status());
        }
        return response;
    }

    @Retryable(
            retryFor = {Exception.class},
            maxAttemptsExpression = "${dns.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${dns.retry.backoff-delay:1000}")
    )
    public APIResponse postResponse(APIRequestContext request, String url, RequestOptions requestOptions) {
        APIResponse response = request.post(url, requestOptions);
        if (response.status() != 200) {
            log.warn("DNS returned {}. Triggering retry...", response.status());
            throw new RuntimeException("DNS error: " + response.status());
        }
        return response;
    }

    private static final Pattern PRODUCT_BUY_HASH_PATTERN = Pattern.compile(
            "\"type\"\\s*:\\s*\"product-buy\"\\s*,\\s*\"hash\"\\s*:\\s*\"([a-f0-9]{32,})\"",
            Pattern.CASE_INSENSITIVE
    );
}