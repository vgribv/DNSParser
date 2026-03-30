package ru.vgribv.parser.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.vgribv.parser.dto.DiscountResultDto;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final Path userDataDir = Paths.get("dns_real_profile");
    private final String linkPrefix;
    private final String linkProductsFilters;
    private final String linkReferer;
    private final String linkAjaxState;

    public ParserService(@Lazy ParserService self, ApplicationEventPublisher publisher,
                         ProductRepository productRepository, CategoryRepository categoryRepository,
                         ArchivedProductRepository archivedProductRepository,
                         FileWriteService fileWriteService, @Lazy SendToUserService sendToUserService,
                         @Value("${dns.link.prefix}") String linkPrefix,
                         @Value("${dns.link.products.filters}") String linkProductsFilters,
                         @Value("${dns.link.referer}") String linkReferer,
                         @Value("${dns.link.ajax.state}") String linkAjaxState,
                         PriceHistoryRepository priceHistoryRepository) {
        this.self = self;
        this.publisher = publisher;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.archivedProductRepository = archivedProductRepository;
        this.fileWriteService = fileWriteService;
        this.sendToUserService = sendToUserService;
        this.linkPrefix = linkPrefix;
        this.linkProductsFilters = linkProductsFilters;
        this.linkReferer = linkReferer;
        this.linkAjaxState = linkAjaxState;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    private void initBrowser() {
        this.playwright = Playwright.create();
        this.context = playwright.chromium().launchPersistentContext(userDataDir, new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(false)
                .setIgnoreDefaultArgs(List.of("--enable-automation"))
                .setArgs(Arrays.asList(
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-blink-features=AutomationControlled",
                        "--disable-ipv6",
                        "--disable-dev-shm-usage"
                ))
                .setViewportSize(1280, 720));
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

    @Scheduled(cron = "0 0 8-20 * * *")
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
            for (int i = 0; true; i++) {
                try {
                    page.navigate(linkPrefix + "?p=1",
                            new Page.NavigateOptions()
                                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                                    .setTimeout(60000));
                    page.waitForSelector(".product-buy__price");
                    break;
                } catch (Exception e) {
                    log.error("Попытка {} не удалась: {}", i, e.getMessage(), e);
                    if (i == 2) throw new RuntimeException("Финальный провал", e);
                    Thread.sleep(1000);
                }
            }

            APIRequestContext request = context.request();
            APIResponse response3 = request.get(linkProductsFilters,
                    RequestOptions.create()
                            .setHeader("X-Requested-With", "XMLHttpRequest")
                            .setHeader("Referer", linkReferer)
            );
            String contentType = response3.headers().get("content-type");
            if (response3.status() != 200 || contentType == null || !contentType.contains("application/json")) {
                log.error("⚠️ DNS вернул ошибку или HTML вместо данных. Статус: {}. Тип контента: {}", response3.status(), contentType);
                throw new RuntimeException("Неожиданный тип контента. Возможно, сработал анти-фрод");
            }

            String body = response3.text();
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
                    category = new Category();
                    category.setCategoryId(categoryId);
                    category.setName(itemData1.get("label").getAsString());
                    uniqueCategoryMap.put(categoryId, category);
                }

                int currentPage = 1;
                boolean flag = false;
                while (!flag && currentPage < 100) {
                    showProgress(category.getName(), currentPage);
                    APIResponse response = request.get(linkPrefix + "?category=" + category.getCategoryId() + "&p=" + currentPage++,
                            RequestOptions.create()
                                    .setHeader("X-Requested-With", "XMLHttpRequest")
                                    .setHeader("Referer", linkReferer));

                    String rawText = response.text();
                    if (rawText == null || !rawText.trim().startsWith("{")) {
                        log.warn("❌ Вместо JSON пришло что-то странное в категории {}. Пропускаю...", category.getName());
                        if (rawText != null) {
                            log.warn("Raw body: {}", rawText.substring(0, Math.min(rawText.length(), 100)));
                        }
                        break;
                    }
                    JsonObject jsonObject = JsonParser.parseString(response.text()).getAsJsonObject();
                    if (!jsonObject.has("html") || jsonObject.get("html").isJsonNull() || jsonObject.get("html").getAsString().isBlank()) {
                        log.warn("В категории '{}' (ID: {}) товары не найдены. Пропускаю...",
                                category.getName(), category.getCategoryId());
                        break;
                    }
                    String realHtml = jsonObject.get("html").getAsString();
                    Document document = Jsoup.parse(realHtml);

                    if (!hasNextPage(document)) flag = true;

                    String req = buildPriceRequest(realHtml);
                    String csrfToken = getToken(context.pages().getFirst());

                    APIResponse response2 = context.request().post(linkAjaxState,
                            RequestOptions.create()
                                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                                    .setHeader("x-csrf-token", csrfToken)
                                    .setHeader("x-requested-with", "XMLHttpRequest")
                                    .setHeader("referer", linkReferer)
                                    .setData("data=" + req));

                    JsonObject root = JsonParser.parseString(response2.text()).getAsJsonObject();
                    JsonArray states = root.getAsJsonObject("data").getAsJsonArray("states");

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
                            product.setCategory(category);
                        } else {
                            product = productMap.get(linkId);
                            product.setName(name);
                            product.setDiscountPrice(discountPrice);
                            product.setFullPrice(fullPrice);
                            product.setCategory(category);
                            product.setUpdatedAt(time);
                        }
                        productsToSave.add(product);
                    }
                    Thread.sleep(random.nextInt(100, 500));
                }
            }

            log.info("Этап 4: Сохранение результатов и рассылка...");

            List<Product> newProducts = getNewProductList(productsToSave, productMap);

            DiscountResultDto result = getDiscountResult(productsToSave, oldPriceMap);
            List<Product> priceHasDecreased = result.discountedProducts();
            List<PriceHistory> priceHistory = result.historyRecords();
            System.out.println(priceHistory.size());

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
            archivedProductRepository.archiveOldProducts(time, LocalDateTime.now());
            priceHistoryRepository.deleteHistoryForOldProducts(time);
            productRepository.deleteOldProductsInBatch(time);
        }
        log.info("📊 Итоги транзакции: сохранено/обновлено {} товаров, удалено {} товаров",
                productsToSave.size(), deletedProducts.size());
        return deletedProducts;
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

    private String getToken(Page page) {
        String csrfToken = (String) page.evaluate("() => document.querySelector('meta[name=\"csrf-token\"]').content");

        if (csrfToken == null || csrfToken.isEmpty())
            csrfToken = (String) page.evaluate("() => window.Config.csrf");

        return csrfToken;
    }

    private String buildPriceRequest(String html) {
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select(".catalog-product");

        JsonObject reqRoot = new JsonObject();
        reqRoot.addProperty("type", "product-buy");
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

    private boolean hasNextPage(Document doc) {
        Element nextButton = doc.selectFirst(".pagination-widget__page-link_next:not(.pagination-widget__page-link_disabled)");
        return nextButton != null;
    }

    private void showProgress(String categoryName, int currentPage) {
        String text = String.format("\r⏳ Категория: %-20s | Страница: %d", categoryName, currentPage);
        System.out.print(text);
    }

}
