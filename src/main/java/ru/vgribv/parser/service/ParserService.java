package ru.vgribv.parser.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import lombok.Getter;
import org.jsoup.Jsoup;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microsoft.playwright.*;
import jakarta.annotation.PostConstruct;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.vgribv.parser.event.ParsingResultEvent;
import ru.vgribv.parser.entity.Category;
import ru.vgribv.parser.entity.Product;
import ru.vgribv.parser.repository.CategoryRepository;
import ru.vgribv.parser.repository.ProductRepository;
import ru.vgribv.parser.repository.UserTelegramRepository;
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
public class ParserService {

    private final ParserService self;

    @Getter
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ApplicationEventPublisher publisher;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final FileWriteService fileWriteService;
    private final SendToUserService sendToUserService;
    private Playwright playwright;
    private BrowserContext context;
    private final Path userDataDir = Paths.get("dns_real_profile");


    public ParserService (@Lazy ParserService self, ApplicationEventPublisher publisher,
                          ProductRepository productRepository, CategoryRepository categoryRepository,
                          FileWriteService fileWriteService, @Lazy SendToUserService sendToUserService) {
        this.self = self;
        this.publisher = publisher;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.fileWriteService = fileWriteService;
        this.sendToUserService = sendToUserService;
    }

    private void initBrowser(){
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

    private void closeBrowser(){
        if (this.context != null)
            context.close();
        if (this.playwright != null){
            playwright.close();
        }
    }

    @PreDestroy
    public void cleanup(){
        closeBrowser();
    }

    @Scheduled(cron = "0 0 8-20 * * *")
    public void scheduledRun(){
        if (running.compareAndSet(false, true)){
            self.executeAsync();
        }
    }

    public boolean manualRun(){
        if (running.compareAndSet(false, true)){
            self.executeAsync();
            return true;
        }
        return false;
    }

    @Async
    @Transactional
    public void executeAsync(){
        try {
            parseGoods();
            publisher.publishEvent(new ParsingResultEvent(true, "✅ Парсинг успешно завершен."));
        } catch (Exception e){
            publisher.publishEvent(new ParsingResultEvent(false, "❌ Парсинг завершен с ошибкой."));
            throw new RuntimeException(e);
        } finally {
            running.set(false);
        }
    }



    private void parseGoods(){

        try {
            Files.createDirectories(Paths.get("data"));
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать папку. " + e);
        }
        LocalDateTime time = LocalDateTime.now();

        try {
            System.out.println("Запуск браузера...");
            initBrowser();
            try {
                //context.route("**/*.{png,jpg,jpeg,svg,webp,gif}", Route::abort);
                Page page = context.pages().getFirst();

                for (int i = 0; true; i++) {
                    try {
                        page.navigate("https://www.dns-shop.ru/catalog/markdown/?p=1",
                                new Page.NavigateOptions()
                                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                                        .setTimeout(60000));
                        page.waitForSelector(".product-buy__price");
                        break;
                    } catch (Exception e) {
                        System.err.println("Попытка " + i + " не удалась: " + e.getMessage());
                        if (i == 2) throw new RuntimeException("Финальный провал", e);
                        Thread.sleep(1000);
                    }
                }

                APIRequestContext request = context.request();
                APIResponse response3 = request.get("https://www.dns-shop.ru/catalogMarkdown/markdown/products-filters/",
                        RequestOptions.create()
                                .setHeader("X-Requested-With", "XMLHttpRequest")
                                .setHeader("Referer", "https://www.dns-shop.ru")
                );
                String contentType = response3.headers().get("content-type");
                if (response3.status() != 200 || contentType == null || !contentType.contains("application/json")) {
                    System.err.println("⚠️ DNS вернул ошибку или HTML вместо данных. Статус: " + response3.status());
                    throw new RuntimeException("Сессия устарела или сработала защита (Anti-ru.vgribv.dns.bot)");
                }

                String body = response3.text();
                if (!body.trim().startsWith("{")) {
                    throw new RuntimeException("Получен некорректный формат данных (вероятно, капча)");
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

                Iterable<Product> allProducts = productRepository.findAll();
                Map<String, Product> productMap = new HashMap<>();
                for (Product product : allProducts) {
                    productMap.put(product.getLinkId(), product);
                }
                List<Product> productsToSave = new ArrayList<>();
                Random random = new Random();

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
                        APIResponse response = request.get("https://www.dns-shop.ru/catalog/markdown/?category=" + category.getCategoryId() + "&p=" + currentPage++,
                                RequestOptions.create()
                                        .setHeader("X-Requested-With", "XMLHttpRequest")
                                        .setHeader("Referer", "https://www.dns-shop.ru"));

                        JsonObject jsonObject = JsonParser.parseString(response.text()).getAsJsonObject();
                        if (!jsonObject.has("html") || jsonObject.get("html").isJsonNull()) {
                            System.err.println("В категории " + category.getCategoryId() + " товаров нет.");
                            break;
                        }
                        String realHtml = jsonObject.get("html").getAsString();
                        Document document = Jsoup.parse(realHtml);

                        if (!hasNextPage(document)) flag = true;

                        String req = buildPriceRequest(realHtml);
                        String csrfToken = getToken(context.pages().getFirst());

                        APIResponse response2 = context.request().post("https://www.dns-shop.ru/ajax-state/product-buy/",
                                RequestOptions.create()
                                        .setHeader("Content-Type", "application/x-www-form-urlencoded")
                                        .setHeader("x-csrf-token", csrfToken)
                                        .setHeader("x-requested-with", "XMLHttpRequest")
                                        .setHeader("referer", "https://www.dns-shop.ru")
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
                                productMap.put(linkId, product);
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
                            System.out.println(product);
                        }
                        Thread.sleep(random.nextInt(100, 500));
                    }
                }


                if (!uniqueCategoryMap.isEmpty()){
                    categoryRepository.saveAllAndFlush(uniqueCategoryMap.values());
                }
                if (!productsToSave.isEmpty()){
                    productRepository.saveAllAndFlush(productsToSave);
                }

                List<Product> newProducts = getNewProductList(productsToSave, productMap);
                List<Product> priceHasDecreased = getProductListWherePriceHasDecreased(productsToSave, productMap);
                List<Product> deletedProducts = productRepository.findAllByUpdatedAtBefore(time);

                productRepository.deleteByUpdatedAtBefore(time);
                fileWriteService.writeFile(newProducts, priceHasDecreased, deletedProducts);
                try {
                    sendToUserService.sendGoodsToUser(newProducts, priceHasDecreased, deletedProducts);
                    System.out.println("Парсинг успешно завершен.");
                } catch (RuntimeException e){
                    System.err.println("Парсинг завершен, но пользователь не получил список товаров");
                }
            } catch (RuntimeException e){
                throw new RuntimeException("Ошибка в блоке запуска браузера. ", e);
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Парсинг прерван из-за ошибки: ", e);
        } catch (Exception e) {
            throw new RuntimeException("Критическая системная ошибка: ", e);
        } finally {
            closeBrowser();
        }
    }


    public List<Product> getNewProductList(List<Product> productsToSave, Map<String, Product> productMap){

        List<Product> bufferNew = new ArrayList<>();
        for (Product product : productsToSave) {
            if (!productMap.containsKey(product.getLinkId())) {
                bufferNew.add(product);
            }
        }
        return bufferNew;
    }

    public List<Product> getProductListWherePriceHasDecreased(List<Product> productsToSave, Map<String, Product> productMap) {

        List<Product> bufferPriceHasDecreased = new ArrayList<>();

        for (Product product: productsToSave){
            Product old =  productMap.get(product.getLinkId());
            if (old != null && old.getDiscountPrice() > product.getDiscountPrice()) {
                bufferPriceHasDecreased.add(product);
            }
        }
        return bufferPriceHasDecreased;
    }

    public String getToken(Page page) {
        String csrfToken = (String) page.evaluate("() => document.querySelector('meta[name=\"csrf-token\"]').content");

        if (csrfToken == null || csrfToken.isEmpty())
            csrfToken = (String) page.evaluate("() => window.Config.csrf");

        return csrfToken;
    }

    public String buildPriceRequest(String html) {
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

    public boolean hasNextPage(Document doc) {
        Element nextButton = doc.selectFirst(".pagination-widget__page-link_next:not(.pagination-widget__page-link_disabled)");
        return nextButton != null;
    }

}
