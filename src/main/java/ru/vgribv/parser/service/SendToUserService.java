package ru.vgribv.parser.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import ru.vgribv.parser.bot.TelegramBot;
import ru.vgribv.parser.entity.*;
import ru.vgribv.parser.repository.SearchFilterRepository;
import ru.vgribv.parser.repository.TrackerRepository;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Transactional
public class SendToUserService {

    private final TelegramBot telegramBot;
    private final TrackerRepository trackerRepository;
    private final SearchFilterRepository searchFilterRepository;

    public SendToUserService(TrackerRepository trackerRepository, TelegramBot telegramBot, SearchFilterRepository searchFilterRepository) {
        this.trackerRepository = trackerRepository;
        this.telegramBot = telegramBot;
        this.searchFilterRepository = searchFilterRepository;
    }

    public void sendGoodsToUser(List<Product> newProducts, List<Product> priceHasDecreased,
                                List<Product> deletedProducts) {
        Map<Long, List<Product>> outputNewProductsFilterMap = new HashMap<>();
        Map<Long, List<Product>> outputPriceHasDecreasedFilterMap = new HashMap<>();
        Map<Long, List<Product>> outputPriceHasDecreasedTrackerMap = new HashMap<>();
        Map<Long, List<Product>> outputDeletedProductsTrackerMap = new HashMap<>();
        Map<String, Product> newProductsMap = newProducts.stream()
                .collect(Collectors.toMap(Product::getLinkId, product -> product));
        Map<String, Product> priceHasDecreasedMap = priceHasDecreased.stream()
                .collect(Collectors.toMap(Product::getLinkId, product -> product));
        Map<String, Product> deletedProductsMap = deletedProducts.stream()
                .collect(Collectors.toMap(Product::getLinkId, product -> product));

        processTrackers(priceHasDecreasedMap, outputPriceHasDecreasedTrackerMap, deletedProductsMap, outputDeletedProductsTrackerMap);
        processFilters(newProductsMap, outputNewProductsFilterMap, priceHasDecreasedMap, outputPriceHasDecreasedFilterMap);

        //Iterable<UserTelegram> userTelegramIsActiveIterable = userTelegramRepository.findAllByIsActive(true);

        Set<Long> allChatIds =  new HashSet<>();
        allChatIds.addAll(outputNewProductsFilterMap.keySet());
        allChatIds.addAll(outputPriceHasDecreasedTrackerMap.keySet());
        allChatIds.addAll(outputPriceHasDecreasedFilterMap.keySet());
        allChatIds.addAll(outputDeletedProductsTrackerMap.keySet());
        for (long chatId : allChatIds) {
            Set<String> sentProductsIds = new HashSet<>();
            StringBuilder message = new StringBuilder();

            List<Product> trackerGoods = outputPriceHasDecreasedTrackerMap.get(chatId);
            if (trackerGoods != null &&  !trackerGoods.isEmpty()) {
                message.append("🎯 <b>Товары из Вашего списка, которые подешевели:</b>\n\n");
                for (Product product : trackerGoods) {
                    appendProductLineWherePriceHasDecreased(message, product);
                    sentProductsIds.add(product.getLinkId());
                }
            }

            List<Product> deleted = outputDeletedProductsTrackerMap.get(chatId);
            if (deleted != null && !deleted.isEmpty()) {
                message.append("❌ <b>Эти товары больше недоступны:</b>\n\n");
                for (Product p : deleted) {
                    message.append("• ").append(p.getName()).append("\n");
                    trackerRepository.deleteTrackerByChatIdAndLink(chatId, p.getLinkId());
                }
            }

            List<Product> newProductsList = outputNewProductsFilterMap.get(chatId);
            if (newProductsList != null && !newProductsList.isEmpty()) {
                message.append("🎯 <b>Новые товары по Вашим фильтрам</b>\n\n");
                for (Product product : newProductsList) {
                    appendNewProductLine(message, product);
                }
            }

            List<Product> filterGoods = outputPriceHasDecreasedFilterMap.get(chatId);
            if (filterGoods != null && !filterGoods.isEmpty()) {
                boolean headerAdded = false;
                for (Product product : filterGoods) {
                    if (!sentProductsIds.contains(product.getLinkId())) {
                        if (!headerAdded) {
                            message.append("🔍 <b>Товары по Вашим фильтрам, которые подешевели:</b>\n\n");
                            headerAdded = true;
                        }
                        appendProductLineWherePriceHasDecreased(message, product);
                        sentProductsIds.add(product.getLinkId());
                    }
                }
            }

            if (!message.isEmpty()) {
                telegramBot.sendMessageText(chatId, message.toString());
            }
        }
    }

    private void processFilters(Map<String, Product> newProductsMap, Map<Long, List<Product>> outputNewProductsFilterMap,
                                Map<String, Product> priceHasDecreasedMap, Map<Long, List<Product>> outputPriceHasDecreasedFilterMap) {
        List<SearchFilter> filters = StreamSupport.stream(searchFilterRepository.findAll().spliterator(), false).toList();

        Map<Product, String> preparedProductNames = new HashMap<>();
        newProductsMap.values().forEach(product -> preparedProductNames.put(product, product.getName().toLowerCase()));
        priceHasDecreasedMap.values().forEach(product -> preparedProductNames.put(product, product.getName().toLowerCase()));

        Map<SearchFilter, String> preparedKeywords = new HashMap<>();
        for (SearchFilter filter : filters ) {
            String keyword = filter.getKeyword();
            if (keyword != null) {
                preparedKeywords.put(filter, keyword.toLowerCase());
            }
        }

        checkAndAdd(filters, preparedProductNames, preparedKeywords, newProductsMap, outputNewProductsFilterMap);
        checkAndAdd(filters, preparedProductNames, preparedKeywords, priceHasDecreasedMap, outputPriceHasDecreasedFilterMap);
    }

    private void processTrackers(Map<String, Product> priceHasDecreasedMap, Map<Long, List<Product>> outputPriceHasDecreasedTrackerMap,
                                 Map<String, Product> deletedProductsMap, Map<Long, List<Product>> outputDeletedProductsTrackerMap) {
        for (Tracker track :  trackerRepository.findAll()) {
            String link = track.getLink();
            Long chatId = track.getChatId();
            checkAndAdd(link, chatId, priceHasDecreasedMap, outputPriceHasDecreasedTrackerMap);
            checkAndAdd(link, chatId, deletedProductsMap, outputDeletedProductsTrackerMap);
        }
    }

    private void checkAndAdd(List<SearchFilter> filters, Map<Product, String> preparedProductName,
                             Map<SearchFilter, String> preparedKeywords, Map<String, Product> productsMap,
                             Map<Long, List<Product>> outputProductsFilterMap) {
        for (Product product: productsMap.values()) {
            String productName = preparedProductName.get(product);
            for (SearchFilter filter: filters){
                String filterKeyword =  preparedKeywords.get(filter);
                if (matchesFilter(product, productName, filter, filterKeyword)){
                    Long chatId = filter.getChatId();
                    outputProductsFilterMap.computeIfAbsent(chatId, _ -> new ArrayList<>()).add(product);
                }
            }
        }
    }

    private void checkAndAdd (String link, Long chatId, Map<String, Product> productsMap,
                              Map<Long, List<Product>> outputProductsMap){
        if (productsMap.containsKey(link)) {
            outputProductsMap.computeIfAbsent(chatId, _ -> new ArrayList<>()).add(productsMap.get(link));
        }
    }


    private boolean matchesFilter(Product product, String productName, SearchFilter filter,  String filterKeyword) {
        if (Optional.ofNullable(filter.getMaxPrice()).
        filter(max -> max > 0 && product.getDiscountPrice() > max).isPresent()){
            return false;
        }

        String filterCategory = filter.getCategory();
        if (filterCategory != null && !filterCategory.isBlank()) {
            Category productCategory = product.getCategory();
            if (productCategory == null || !productCategory.getName().equalsIgnoreCase(filterCategory)) {
                return false;
            }
        }
        if (filterKeyword != null && !filterKeyword.isBlank()) {
            return productName != null && productName.contains(filterKeyword);
        }
        return true;
    }

    private void appendProductLineWherePriceHasDecreased(StringBuilder sb, Product p) {
        sb.append("<b>").append(p.getName()).append("</b>\n")
                .append("💰 Цена: <s>").append(p.getOldDiscountPrice()).append("</s> ")
                .append(p.getDiscountPrice()).append(" руб.\n")
                .append("🔗 <a href=\"https://www.dns-shop.ru/catalog/markdown/")
                .append(p.getLinkId()).append("/\">Купить</a>\n\n");
    }

    private void appendNewProductLine(StringBuilder sb, Product p) {
        sb.append("<b>").append(p.getName()).append("</b>\n");
        sb.append("💰 Цена: ");

        if (p.getFullPrice() != null && p.getFullPrice() > 0 &&
                p.getDiscountPrice() != null && p.getFullPrice() > p.getDiscountPrice()) {
            sb.append("<s>").append(p.getFullPrice()).append("</s> ");
        }
        Integer discountPrice = p.getDiscountPrice();
                sb.append(discountPrice != null ? discountPrice : "уточняется").append(" руб.\n")
                .append("🔗 <a href=\"https://www.dns-shop.ru/catalog/markdown/")
                .append(p.getLinkId()).append("/\">Купить</a>\n\n");
    }
}
