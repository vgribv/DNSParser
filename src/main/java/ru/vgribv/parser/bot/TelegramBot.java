package ru.vgribv.parser.bot;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.vgribv.parser.entity.Product;
import ru.vgribv.parser.entity.SearchFilter;
import ru.vgribv.parser.entity.Tracker;
import ru.vgribv.parser.entity.UserTelegram;
import ru.vgribv.parser.repository.ProductRepository;
import ru.vgribv.parser.repository.SearchFilterRepository;
import ru.vgribv.parser.repository.TrackerRepository;
import ru.vgribv.parser.repository.UserTelegramRepository;
import ru.vgribv.parser.service.ParserService;

import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;


@Component
public class TelegramBot implements LongPollingSingleThreadUpdateConsumer, SpringLongPollingBot {


    private final TelegramClient telegramClient;
    private final String botToken;
    private final String path;
    private final ProductRepository productRepository;
    private final TrackerRepository trackerRepository;
    private final UserTelegramRepository userTelegramRepository;
    private final Map<Long, String> userState = new HashMap<>();
    private final Map<Long, Integer> userMessageIdTemp = new HashMap<>();
    private final Map<Long, DeleteMessage> userStateMessage = new HashMap<>();
    private final Map<Long, SearchFilter> tempFilterValues = new HashMap<>();
    private final Map<Long, Tracker> tempTrackerValues = new HashMap<>();
    private final Map<Long, Integer> userPages = new HashMap<>();
    private final SearchFilterRepository searchFilterRepository;
    @Lazy
    private final ParserService parserService;

    public TelegramBot(@Qualifier("telegramClientWithoutProxy") TelegramClient telegramClient, @Value("${bot.token}") String botToken,
                       @Value("${project.path}") String path, ProductRepository productRepository,
                       TrackerRepository trackerRepository, UserTelegramRepository userTelegramRepository,
                       SearchFilterRepository searchFilterRepository, ParserService parserService) {
        this.telegramClient = telegramClient;
        this.botToken = botToken;
        this.productRepository = productRepository;
        this.trackerRepository = trackerRepository;
        this.userTelegramRepository = userTelegramRepository;
        this.path = path;
        this.searchFilterRepository = searchFilterRepository;
        this.parserService = parserService;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {

            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            DeleteMessage deleteMessageReply = DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(update.getMessage().getMessageId())
                    .build();

            if(userState.containsKey(chatId)){
                String state = userState.get(chatId);
                DeleteMessage deleteMessage2 = userStateMessage.get(chatId);
                DeleteMessage deleteMessage = new DeleteMessage(String.valueOf(chatId), update.getMessage().getMessageId());
                SearchFilter filter = tempFilterValues.get(chatId);
                Tracker tracker = tempTrackerValues.get(chatId);
                boolean flag = false;
                boolean flagRepeat = false;
                String message = messageText.toLowerCase();
                switch (state) {
                    case "WAITING_FOR_WORD" -> {
                        if (filter.getKeyword() != null && filter.getKeyword().equals(message))
                            flagRepeat = true;
                        else
                            filter.setKeyword(message);
                        flag = true;
                    }
                    case "WAITING_FOR_CATEGORY" -> {
                        if (filter.getCategory() != null && filter.getCategory().equals(message))
                            flagRepeat = true;
                        else
                            filter.setCategory(messageText.toLowerCase());
                        flag = true;
                    }
                    case "WAITING_FOR_PRICE" -> {
                        try {
                            int price = Integer.parseInt(messageText);
                            if (filter.getMaxPrice() != null && filter.getMaxPrice().equals(price))
                                flagRepeat = true;
                            else
                                filter.setMaxPrice(price);
                            flag = true;
                        } catch (NumberFormatException e) {
                            sendMessageText(chatId, "⚠️ Пожалуйста, введите целое число!");
                            return;
                        }
                    }
                    case "WAITING_FOR_LINK" -> {
                        String productId = messageText.replaceAll(".*/markdown/|/.*", "");
                        if(trackerRepository.existsTrackerByChatIdAndLink(chatId, productId)){
                            sendMessageText(chatId, "❌ Вы уже отслеживаете этот товар.");
                        } else {
                            Optional<Product> productOptional = productRepository.findProductByLinkId(productId);
                            productOptional.ifPresentOrElse(product -> {
                                        tracker.setChatId(chatId);
                                        tracker.setLink(productId);
                                        tracker.setName(product.getName());
                                        trackerRepository.save(tracker);
                                        List<Tracker> trackerList = trackerRepository.findAllByChatId(chatId);
                                        editMenu(chatId, userMessageIdTemp.get(chatId), "Список отслеживаемых товаров",
                                                KeyboardFactory.getTrackersMenu(userPages.getOrDefault(chatId, 0), trackerList));
                                        tempTrackerValues.remove(chatId);
                                    },
                                    () ->
                                        sendMessageText(chatId, "❌ Такого товара нет в базе данных. Попробуйте другую ссылку."));
                        }
                    }
                }
                if (flag){
                    String sb = "Ключевое слово: " + tempFilterValues.get(chatId).getKeyword() + "\n" +
                            "Категория: " + tempFilterValues.get(chatId).getCategory() + "\n" +
                            "Максимальная цена: " + tempFilterValues.get(chatId).getMaxPrice();
                    if (!flagRepeat) editMenu(chatId, userMessageIdTemp.get(chatId), sb, KeyboardFactory.setFilterMenu());
                }
                userMessageIdTemp.remove(chatId);
                userState.remove(chatId);
                userStateMessage.remove(chatId);
                for (int i = 0; true; i++){
                    try {
                        telegramClient.execute(deleteMessage);
                        if (deleteMessage2 != null)telegramClient.execute(deleteMessage2);
                        break;
                    } catch (TelegramApiException e) {
                        if (i == 3){
                            System.err.println("Не удалось удалить сообщение. " + e);
                            break;
                        }
                    }
                }
            }

            switch (messageText){
                case "/start", "/menu" -> {
                    UserTelegram userTelegram = userTelegramRepository.findByChatId(chatId).orElseGet(() -> new UserTelegram(chatId));
                    userTelegram.setActive(true);
                    userTelegramRepository.save(userTelegram);
                    menuReply(chatId);
                }
                case "Все товары" -> {
                    try {
                        menuAllGoods(chatId);
                        telegramClient.execute(deleteMessageReply);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                }
                case "Фильтры" -> {
                    try {
                        userPages.put(chatId, 0);
                        menuFilters(chatId);
                        telegramClient.execute(deleteMessageReply);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                }
                case "Отследить" -> {
                    try {
                        userPages.put(chatId, 0);
                        menuTrack(chatId);
                        telegramClient.execute(deleteMessageReply);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                }
                case "Парсинг" -> {
                    if (parserService.manualRun()){
                        sendMessageText(chatId, "✅ Парсинг успешно запущен.");
                    } else {
                        sendMessageText(chatId, "⏳ Ошибка: Задача уже выполняется.");
                    }
                }
                case "Назад" -> {
                    menuReply(chatId);
                    try {
                        telegramClient.execute(deleteMessageReply);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

        }
        else if (update.hasCallbackQuery()) {
            CallbackQuery callback = update.getCallbackQuery();
            String callData = callback.getData();
            long chatId = callback.getMessage().getChatId();
            int messageId = callback.getMessage().getMessageId();

            if (callData.startsWith("editFilter_")) {
                long filterId = Long.parseLong(callData.split("_")[1]);
                SearchFilter searchFilter = searchFilterRepository.getFirstById(filterId);
                editMenu(chatId, messageId, "Редактировать фильтр", KeyboardFactory.editFilterMenu(filterId));
                tempFilterValues.put(chatId, searchFilter);
            } else if (callData.startsWith("filterPage_")){
                List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                int currentPage = Integer.parseInt(callData.split("_")[1]);
                userPages.put(chatId, currentPage);
                editMenu(chatId, messageId, "Список фильтров", KeyboardFactory.getFiltersMenu(currentPage, searchFilters));
            } else if (callData.startsWith("trackerPage_")){
                int currentPage = Integer.parseInt(callData.split("_")[1]);
                userPages.put(chatId, currentPage);

                List<Tracker> trackerList = trackerRepository.getAllByChatId(chatId);
                editMenu(chatId, messageId, "Список отслеживаемых товаров",
                        KeyboardFactory.getTrackersMenu(currentPage, trackerList));
            } else if (callData.startsWith("editTracker_")) {
                String link = String.valueOf(callData.split("_")[1]);
                StringBuilder sb = new StringBuilder();
                Optional<Product> productOptional = productRepository.findProductByLinkId(link);
                if (productOptional.isPresent()) {
                    Product product = productOptional.get();
                    sb.append("<b>").append(product.getName()).append("</b>\n").append("💰 Цена: <s>").append(product.getFullPrice()).
                            append("</s> ").append(product.getDiscountPrice()).append(" руб.\n").append("\uD83D\uDCCA Категория: ")
                            .append(product.getCategory().getName()).append("\n");
                } else {
                    sendMessageText(chatId, "Ошибка. Попробуйте еще раз.");
                }

                Tracker tracker = trackerRepository.findFirstByChatIdAndLink(chatId, link);
                editMenu(chatId, messageId, sb.toString(), KeyboardFactory.editTrackerMenu(link));
                tempTrackerValues.put(chatId, tracker);
            } else if (callData.equals(InlineButton.FILTERS.getData())){
                List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                editMenu(chatId, messageId, "Настройка фильтров", KeyboardFactory.getFiltersMenu(userPages.get(chatId), searchFilters));
            } else if (callData.equals(InlineButton.BACK_TRACK.getData())){
                List<Tracker> trackerList = trackerRepository.getAllByChatId(chatId);

                editMenu(chatId, messageId, "Список отслеживаемых товаров",
                        KeyboardFactory.getTrackersMenu(userPages.get(chatId), trackerList));

            } else if (callData.equals(InlineButton.GOODS_TXT.getData())) {
//                ldb.listGoods();
                sendTextFile(chatId, path + "data/goods.txt");
            } else if (callData.equals(InlineButton.GOODS_PERCENT_TXT.getData())) {
//                ldb.listGoodsMaximumDiscount();
                sendTextFile(chatId, path + "data/listGoodsMaximumDiscount.txt");
            } else if (callData.equals(InlineButton.GOODS_LOG.getData())) {
                sendTextFile(chatId, path + "data/goodsLog.txt");
            } else if (callData.equals(InlineButton.NEW_FILTER.getData())) {
                tempFilterValues.put(chatId, new SearchFilter(chatId));
                editMenu(chatId, messageId, "Заполните фильтр", KeyboardFactory.setFilterMenu());
            } else if (callData.equals(InlineButton.TRACK_NEW.getData())) {
                tempTrackerValues.put(chatId, new Tracker());
                try {
                    cancelInputLinkMenu(chatId);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
                userState.put(chatId, "WAITING_FOR_LINK");
                userMessageIdTemp.put(chatId, messageId);
            } else if (callData.equals(InlineButton.SET_FILTER_WORD.getData())) {
                int prevMessageId = sendMessageText(chatId, "Введите ключевое слово:");
                userState.put(chatId, "WAITING_FOR_WORD");
                userMessageIdTemp.put(chatId, messageId);
                userStateMessage.put(chatId, new DeleteMessage(String.valueOf(chatId), prevMessageId));
            } else if (callData.equals(InlineButton.SET_FILTER_CATEGORY.getData())) {
                int prevMessageId = sendMessageText(chatId, "Введите категорию:");
                userState.put(chatId, "WAITING_FOR_CATEGORY");
                userMessageIdTemp.put(chatId, messageId);
                userStateMessage.put(chatId, new DeleteMessage(String.valueOf(chatId), prevMessageId));
            } else if (callData.equals(InlineButton.SET_FILTER_PRICE.getData())) {
                int prevMessageId = sendMessageText(chatId, "Введите максимальную цену:");
                userState.put(chatId, "WAITING_FOR_PRICE");
                userMessageIdTemp.put(chatId, messageId);
                userStateMessage.put(chatId, new DeleteMessage(String.valueOf(chatId), prevMessageId));
            } else if (callData.equals(InlineButton.SAVE_FILTER.getData()) || callData.equals(InlineButton.SAVE_EDIT_FILTER.getData())) {
                try {
                    searchFilterRepository.save(tempFilterValues.get(chatId));
                    tempFilterValues.remove(chatId);
                    answerCallback(update.getCallbackQuery().getId(), "Фильтр сохранен");
                    List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                    editMenu(chatId, messageId, "Список фильтров", KeyboardFactory.getFiltersMenu(userPages.get(chatId), searchFilters));
                } catch (RuntimeException e) {
                    throw new RuntimeException("Ошибка сохранения фильтра", e);
                }
            } else if (callData.startsWith("deleteFilter_")) {
                System.out.println(callData);
                try {
                    long filterId = Long.parseLong(callData.split("_")[1]);
                    searchFilterRepository.deleteById(filterId);
                    answerCallback(update.getCallbackQuery().getId(), "Фильтр удален");
                    List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                    editMenu(chatId, messageId, "Список фильтров", KeyboardFactory.getFiltersMenu(userPages.getOrDefault(chatId, 0), searchFilters));
                } catch (RuntimeException e){
                    throw new RuntimeException("Ошибка удаления фильтра", e);
                }
            } else if (callData.equals(InlineButton.DELETE_TRACKER.getData())) {
                try {
                    trackerRepository.deleteTrackerByChatIdAndId(chatId, tempTrackerValues.get(chatId).getId());
                    List<Tracker> trackerList = trackerRepository.getAllByChatId(chatId);
                    answerCallback(update.getCallbackQuery().getId(), "Ссылка удалена");

                    editMenu(chatId, messageId, "Список отслеживаемых товаров", KeyboardFactory.getTrackersMenu(userPages.get(chatId), trackerList));
                }
                catch (RuntimeException e) {
                    throw new RuntimeException("Ошибка в удалении трекера. ", e);
                }
            } else if (callData.equals(InlineButton.CANCEL.getData())) {
                userState.remove(chatId);
                try {
                    telegramClient.execute(new DeleteMessage(String.valueOf(chatId), callback.getMessage().getMessageId()));
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
            answerCallback(update.getCallbackQuery().getId(), "");
        }
    }

    private void editMenu(long chatId, int messageId, String newText, InlineKeyboardMarkup markup) {
        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(newText)
                .parseMode("HTML")
                .replyMarkup(markup)
                .build();
        try {
            telegramClient.execute(edit);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Не удалось изменить меню: ", e);
        }
    }

    public int sendMessageText(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode(ParseMode.HTML)
                .build();
        try {
            Message sentMessage = telegramClient.execute(message);
            return sentMessage.getMessageId();
        } catch (TelegramApiException e) {
            throw new RuntimeException("Ошибка отправки сообщения пользователю " + chatId, e);
        }
    }

    public void menuFilters (long chatId) throws TelegramApiException {
        List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
        InlineKeyboardMarkup inlineKeyboardMarkup = KeyboardFactory.getFiltersMenu(userPages.get(chatId),searchFilters);

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Список фильтров")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        telegramClient.execute(sendMessage);
    }

    public void menuAllGoods(long chatId) throws TelegramApiException {
        InlineKeyboardMarkup inlineKeyboardMarkup = KeyboardFactory.getGoodsMenu();
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Выберите файл")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        telegramClient.execute(sendMessage);
    }

    public void menuTrack (long chatId) {

        List<Tracker> trackerList = trackerRepository.findAllByChatId(chatId);
        InlineKeyboardMarkup inlineKeyboardMarkup = KeyboardFactory.getTrackersMenu(userPages.get(chatId), trackerList);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Список отслеживаемых товаров")
                .replyMarkup(inlineKeyboardMarkup)
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e){
            throw new RuntimeException(e);
        }
    }

    public void menuReply(long chatId) {
        try {
            telegramClient.execute(KeyboardFactory.mainMenuReply(chatId));
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public void cancelInputLinkMenu (long chatId) throws TelegramApiException {
        InlineKeyboardMarkup inlineKeyboardMarkup = KeyboardFactory.inputLinkMenu();
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Введите ссылку")
                .replyMarkup(inlineKeyboardMarkup)
                .build();
        telegramClient.execute(sendMessage);
    }

    public void sendTextFile(long chatId, String filePath) {
        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(new File(filePath)))
                .build();
        try {
            telegramClient.execute(sendDocument);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void answerCallback(String queryId, String text) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(queryId)
                .text(text)
                .showAlert(false)
                .build();
        try {
            telegramClient.execute(answer);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
