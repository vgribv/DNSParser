package ru.vgribv.parser.service;

import org.springframework.stereotype.Service;
import ru.vgribv.parser.bot.TelegramBot;
import ru.vgribv.parser.entity.Product;
import ru.vgribv.parser.entity.SearchFilter;
import ru.vgribv.parser.entity.Tracker;
import ru.vgribv.parser.repository.SearchFilterRepository;
import ru.vgribv.parser.repository.TrackerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
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
        Map<Long, List<Product>> bufferNewProductsMap = new HashMap<>();
        Map<Long, List<Product>> bufferPriceHasDecreasedMap = new HashMap<>();
        Map<Long, List<Product>> bufferDeletedProductsMap = new HashMap<>();

        Map<String, Product> newProductsMap = newProducts.stream()
                .collect(Collectors.toMap(Product::getLinkId, product -> product));
        Map<String, Product> priceHasDecreasedMap = priceHasDecreased.stream()
                .collect(Collectors.toMap(Product::getLinkId, product -> product));
        Map<String, Product> deletedProductsMap = deletedProducts.stream()
                .collect(Collectors.toMap(Product::getLinkId, product -> product));

        for (Tracker track :  trackerRepository.findAll()) {
            String link = track.getLink();
            Long chatId = track.getChatId();
            //checkAndAdd(link, chatId, newProductsMap, bufferNewProductsMap);
            checkAndAdd(link, chatId, priceHasDecreasedMap, bufferPriceHasDecreasedMap);
            checkAndAdd(link, chatId, deletedProductsMap, bufferDeletedProductsMap);
        }

        for (SearchFilter filter: searchFilterRepository.findAll()){
//            matchesFilter(, filter);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Результаты поиска новых товаров:").append("\n\n");
        for (Map.Entry<Long, List<Product>> entry : bufferNewProductsMap.entrySet()) {
            for (Product product : entry.getValue()) {
                sb.append("<b>").append(product.getName()).append("</b>\n").append("💰 Цена: ");
                if(product.getFullPrice() != null && product.getFullPrice() > 0)
                    sb.append("<s>").append(product.getFullPrice()).append("</s> ");
                sb.append(product.getDiscountPrice()).append(" руб.\n").
                        append("🔗 <a href=\"https://www.dns-shop.ru/catalog/markdown/").
                        append(product.getId()).append("/\">Перейти к товару</a>").append("\n\n");
            }
        }

        sb.append("Результаты поиска товаров, которые подешевели:").append("\n\n");
        for (Map.Entry<Long, List<Product>> entry : bufferPriceHasDecreasedMap.entrySet()) {
            for (Product product : entry.getValue()) {
                sb.append("<b>").append(product.getName()).append("</b>\n").append("💰 Цена: ")
                        .append("<s>").append(product.getOldDiscountPrice()).append("</s> ")
                        .append(product.getDiscountPrice()).append(" руб.\n")
                        .append("🔗 <a href=\"https://www.dns-shop.ru/catalog/markdown/")
                        .append(product.getId()).append("/\">Перейти к товару</a>").append("\n\n");
            }
        }




//        for (Map.Entry<Long, List<Product>> entry : notificationsBuffer.entrySet()) {
//            long chatId = entry.getKey();
//            List<Product> foundForUser = entry.getValue();
//            StringBuilder sb = new StringBuilder();
//            sb.append(text).append("\n\n");
//            if (foundForUser.isEmpty()) {
//                telegramBot.sendMessageText(chatId, "К сожалению, ничего не найдено 😔");
//            } else {
//                for (Product product : foundForUser) {
//                    if(text.equals("Результаты поиска новых товаров:"))
//                        sb.append("<b>").append(product.getName()).append("</b>\n").append("💰 Цена: <s>").append(product.getFullPrice()).
//                                append("</s> ").append(product.getDiscountPrice()).append(" руб.\n").
//                                append("🔗 <a href=\"https://www.dns-shop.ru/catalog/markdown/").
//                                append(product.getId()).append("/\">Перейти к товару</a>").append("\n\n");
//                    else if (text.equals("Результаты поиска товаров, которые подешевели:")) sb.append("<b>").append(product.getName()).append("</b>\n").append("💰 Цена: <s>").append(product.getOldDiscountPrice()).
//                            append("</s> ").append(product.getDiscountPrice()).append(" руб.\n").
//                            append("🔗 <a href=\"https://www.dns-shop.ru/catalog/markdown/").
//                            append(product.getId()).append("/\">Перейти к товару</a>").append("\n\n");
//                    else {
//                        if (!product.isGoodPurchased())
//                            sb.append("<b>").append(product.getName()).append("</b>\n").append("💰 Цена: <s>").append(product.getOldDiscountPrice()).
//                                    append("</s> ").append(product.getDiscountPrice()).append(" руб.\n").
//                                    append("🔗 <a href=\"https://www.dns-shop.ru/catalog/markdown/").
//                                    append(product.getId()).append("/\">Перейти к товару</a>").append("\n\n");
//                        else {
//                            sb.append("<b>").append(product.getName()).append("</b>\n").append("❌ Купили за ").append(product.getDiscountPrice()).append("руб.").append("\n\n");
//                            trackerRepository.deleteTrackerByChatIdAndLink(chatId, product.getLinkId());
//                        }
//                    }
//                }
//                telegramBot.sendMessageText(chatId, sb.toString());
//                try {
//                    Thread.sleep(50);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }
    }

    private void checkAndAdd (String link, Long chatId,Map<String, Product> productsMap,
                              Map<Long, List<Product>> bufferProductsMap){
        if (productsMap.containsKey(link)) {
            bufferProductsMap.computeIfAbsent(chatId, _ -> new ArrayList<>()).add(productsMap.get(link));
        }
    }

    private boolean matchesFilter(Product product, SearchFilter filter) {

        if (filter.getCategory() != null && !filter.getCategory().isBlank()) {
            if (!product.getCategory().getName().equalsIgnoreCase(filter.getCategory())) {
                return false;
            }
        }
        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
            String filterWord = filter.getKeyword().toLowerCase();
            String productName = product.getName().toLowerCase();

            if (!productName.contains(filterWord)) {
                return false;
            }
        }
        return filter.getMaxPrice() == 0 || product.getDiscountPrice() <= filter.getMaxPrice();
    }
}
