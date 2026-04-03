package ru.vgribv.parser.bot;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.vgribv.parser.entity.Tracker;
import ru.vgribv.parser.entity.SearchFilter;
import ru.vgribv.parser.entity.UserTelegram;
import ru.vgribv.parser.entity.Product;
import ru.vgribv.parser.entity.Category;
import ru.vgribv.parser.repository.ProductRepository;
import ru.vgribv.parser.repository.SearchFilterRepository;
import ru.vgribv.parser.repository.TrackerRepository;
import ru.vgribv.parser.repository.UserTelegramRepository;
import ru.vgribv.parser.service.ParserService;
import ru.vgribv.parser.service.ReportService;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class TelegramBot implements LongPollingSingleThreadUpdateConsumer, SpringLongPollingBot {

    private final TelegramClient telegramClient;
    private final String botToken;
    private final ProductRepository productRepository;
    private final TrackerRepository trackerRepository;
    private final UserTelegramRepository userTelegramRepository;
    private final SearchFilterRepository searchFilterRepository;
    private final Set<Long> activeUsersCache = ConcurrentHashMap.newKeySet();
    private final Map<Long, String> userState = new HashMap<>();
    private final Map<Long, Integer> userMessageIdTemp = new HashMap<>();
    private final Map<Long, DeleteMessage> userStateMessage = new HashMap<>();
    private final Map<Long, SearchFilter> tempFilterValues = new HashMap<>();
    private final Map<Long, Tracker> tempTrackerValues = new HashMap<>();
    private final Map<Long, Integer> userPages = new HashMap<>();
    @Lazy
    private final ParserService parserService;
    private final ReportService reportService;
    private final KeyboardFactory  keyboardFactory;

    public TelegramBot(@Qualifier("telegramClientWithProxy") TelegramClient telegramClient,
                       @Value("${BOT_TOKEN:none}") String botToken,
                       ProductRepository productRepository, TrackerRepository trackerRepository,
                       UserTelegramRepository userTelegramRepository, SearchFilterRepository searchFilterRepository,
                       ParserService parserService, ReportService reportService, KeyboardFactory keyboardFactory) {
        this.telegramClient = telegramClient;
        this.botToken = botToken;
        this.productRepository = productRepository;
        this.trackerRepository = trackerRepository;
        this.userTelegramRepository = userTelegramRepository;
        this.searchFilterRepository = searchFilterRepository;
        this.parserService = parserService;
        this.reportService = reportService;
        this.keyboardFactory = keyboardFactory;

        log.info("!!! ТЕЛЕГРАМ-БОТ ЗАПУЩЕН. Используемый клиент: {} !!!",
                telegramClient.getClass().getSimpleName());
    }

    @PostConstruct
    public void loadCache() {
        List<Long> ids = userTelegramRepository.findAllIds();
        activeUsersCache.addAll(ids);
        log.info("Загружено {} пользователей в кэш", activeUsersCache.size());
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
            if (isUserNotAuthorized(chatId) && !messageText.equals("/start")) {
                sendMessageText(chatId, "⚠️ Нажмите /start для регистрации.");
                return;
            } else if (messageText.equals("/start")) {
                if (isUserNotAuthorized(chatId)) {
                    User from = update.getMessage().getFrom();
                    registerUser(chatId, from.getFirstName());
                    log.info("Зарегистрирован новый пользователь: {}", from.getFirstName());
                }
                menuReply(chatId);
                return;
            }
            DeleteMessage deleteMessageReply = DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(update.getMessage().getMessageId())
                    .build();

            if (userState.containsKey(chatId)) {
                String state = userState.get(chatId);
                DeleteMessage deleteMessage = new DeleteMessage(String.valueOf(chatId), update.getMessage().getMessageId());
                DeleteMessage deleteMessageTwo = userStateMessage.get(chatId);
                SearchFilter filter = tempFilterValues.get(chatId);
                Tracker tracker = tempTrackerValues.get(chatId);
                boolean flag = false;
                boolean flagRepeat = false;

                switch (state) {
                    case "WAITING_FOR_WORD" -> {
                        if (Objects.equals(filter.getKeyword(), messageText)) {
                            flagRepeat = true;
                        } else {
                            filter.setKeyword(messageText);
                        }
                        flag = true;
                    }
                    case "WAITING_FOR_CATEGORY" -> {
                        if (Objects.equals(filter.getCategory(), messageText)) {
                            flagRepeat = true;
                        } else {
                            filter.setCategory(messageText);
                        }
                        flag = true;
                    }
                    case "WAITING_FOR_PRICE" -> {
                        try {
                            int price = Integer.parseInt(messageText);
                            if (price < 0 || price > 500000) {
                                sendMessageText(chatId, "⚠️ Цена должна быть от 0 до 500 000 руб.");
                                return;
                            }
                            if (Objects.equals(filter.getMaxPrice(), price)) {
                                flagRepeat = true;
                            } else {
                                filter.setMaxPrice(price);
                            }
                            flag = true;
                        } catch (NumberFormatException e) {
                            sendMessageText(chatId, "⚠️ Пожалуйста, введите целое число!");
                            return;
                        }
                    }
                    case "WAITING_FOR_LINK" -> {
                        String productId = messageText.replaceAll(".*/markdown/|/.*", "");

                        if (trackerRepository.existsTrackerByChatIdAndLink(chatId, productId)) {
                            sendMessageText(chatId, "❌ Вы уже отслеживаете этот товар.");
                        } else {
                            Optional<Product> productOptional = productRepository.findProductByLinkId(productId);
                            productOptional.ifPresentOrElse(product -> {
                                        tracker.setLink(productId);
                                        tracker.setName(product.getName());
                                        trackerRepository.save(tracker);
                                        List<Tracker> trackerList = trackerRepository.findAllByChatId(chatId);
                                        editMenu(chatId, userMessageIdTemp.get(chatId), "Список отслеживаемых товаров",
                                                keyboardFactory.getTrackersMenu(userPages.getOrDefault(chatId, 0), trackerList));
                                        tempTrackerValues.remove(chatId);
                                    },
                                    () ->
                                            sendMessageText(chatId, "❌ Такого товара нет в базе данных. Попробуйте другую ссылку."));
                        }
                    }
                }
                if (flag) {
                    if (!flagRepeat) {
                        editMenu(chatId, userMessageIdTemp.get(chatId),
                                "Редактировать фильтр", keyboardFactory.editFilterMenu(filter, false));
                    }
                }

                userMessageIdTemp.remove(chatId);
                userState.remove(chatId);
                userStateMessage.remove(chatId);

                for (int i = 0; true; i++) {
                    try {
                        executeMessage(deleteMessage);
                        if (deleteMessageTwo != null) {
                            executeMessage(deleteMessageTwo);
                        }
                        break;
                    } catch (RuntimeException e) {
                        if (i == 3) {
                            log.error("Не удалось удалить сообщение после {} попыток: ", i, e);
                            break;
                        }
                    }
                }
            }

            switch (messageText) {
                case "/menu" -> menuReply(chatId);

                case "Все товары" -> {
                    menuAllGoods(chatId);
                    executeMessage(deleteMessageReply);
                }

                case "Фильтры" -> {
                    userPages.put(chatId, 0);
                    menuFilters(chatId);
                    executeMessage(deleteMessageReply);
                }

                case "Отследить" -> {
                    userPages.put(chatId, 0);
                    menuTrack(chatId);
                    executeMessage(deleteMessageReply);
                }

                case "Парсинг" -> parserService.manualRun().ifPresentOrElse(p ->
                                sendMessageText(chatId, "✅ Парсинг успешно запущен."),
                        () -> sendMessageText(chatId, "⏳ Ошибка: Задача уже выполняется."));

                case "Назад" -> {
                    menuReply(chatId);
                    executeMessage(deleteMessageReply);
                }
            }

        } else if (update.hasCallbackQuery()) {
            CallbackQuery callback = update.getCallbackQuery();
            String callData = callback.getData();
            long chatId = callback.getMessage().getChatId();
            Integer messageId = callback.getMessage().getMessageId();

            if (callData.startsWith("editFilter_")) {
                SearchFilter searchFilter = searchFilterRepository
                        .getFirstById(Long.parseLong(callData.split("_")[1]));

                editMenu(chatId, messageId, "Редактировать фильтр",
                        keyboardFactory.editFilterMenu(searchFilter, true));
                tempFilterValues.put(chatId, searchFilter);

            } else if (callData.startsWith("filterPage_")) {
                List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                int currentPage = Integer.parseInt(callData.split("_")[1]);
                userPages.put(chatId, currentPage);

                editMenu(chatId, messageId, "Список фильтров",
                        keyboardFactory.getFiltersMenu(currentPage, searchFilters));

            } else if (callData.startsWith("trackerPage_")) {
                int currentPage = Integer.parseInt(callData.split("_")[1]);
                userPages.put(chatId, currentPage);
                List<Tracker> trackerList = trackerRepository.getAllByChatId(chatId);

                editMenu(chatId, messageId, "Список отслеживаемых товаров",
                        keyboardFactory.getTrackersMenu(currentPage, trackerList));

            } else if (callData.startsWith("editTracker_")) {
                String link = String.valueOf(callData.split("_")[1]);
                StringBuilder sb = new StringBuilder();

                deleteMessage(chatId, messageId);
                AtomicReference<String> imgUrl = new AtomicReference<>("");
                productRepository.findProductByLinkId(link).ifPresentOrElse(product -> {
                            Category category = product.getCategory();
                            String typeOfRepair = product.getTypeOfRepair();
                            String categoryName = (category != null) ? category.getName() : "Общая категория";
                            sb.append("<b>").append(product.getName()).append("</b>\n")
                                    .append("💰 Цена: <s>").append(product.getFullPrice()).append("</s> ")
                                    .append("<b>").append(product.getDiscountPrice()).append(" руб.</b>\n")
                                    .append("\uD83D\uDCCA Категория: ").append(categoryName).append("\n")
                                    .append("🛠 Состояние: ").append(product.getCondition()).append("\n")
                                    .append("✨ Внешний вид: ").append(product.getAppearance()).append("\n")
                                    .append("📦 Комплектация: ").append(product.getCompleteness()).append("\n");
                                    if (typeOfRepair != null){
                                        sb.append("🔧 Ремонт: ").append(typeOfRepair).append("\n");
                                    }
                                    imgUrl.set(product.getImageUrl());
                        },
                        () -> sendMessageText(chatId, "Ошибка. Попробуйте еще раз."));

                InputFile file = new InputFile(imgUrl.get());
                SendPhoto sendPhoto = SendPhoto.builder()
                        .chatId(chatId)
                        .caption(sb.toString())
                        .photo(file)
                        .parseMode(ParseMode.HTML)
                        .replyMarkup(keyboardFactory.editTrackerMenu(link))
                        .build();
                sendPhoto(sendPhoto);
                Tracker tracker = trackerRepository.findFirstByChatIdAndLink(chatId, link);
                tempTrackerValues.put(chatId, tracker);

            } else if (callData.equals(InlineButton.FILTERS.getData())) {
                List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                editMenu(chatId, messageId, "Настройка фильтров",
                        keyboardFactory.getFiltersMenu(userPages.get(chatId), searchFilters));

            } else if (callData.equals(InlineButton.BACK_TRACK.getData())) {
                deleteMessage(chatId, messageId);
                menuTrack(chatId);

            } else if (callData.equals(InlineButton.PRODUCT_FILE.getData())) {
                sendFile(chatId, reportService.getProductsReport(), "📄 Ваш файл");

            } else if (callData.equals(InlineButton.PRODUCT_PERCENT_FILE.getData())) {
                sendFile(chatId, reportService.getProductsPercentReport(), "📄 Ваш файл");

            } else if (callData.equals(InlineButton.PRODUCT_LOG.getData())) {
                sendFile(chatId, reportService.getLogReport(), "📄 Ваш файл");

            } else if (callData.equals(InlineButton.NEW_FILTER.getData())) {
                SearchFilter filter = new SearchFilter(chatId);
                tempFilterValues.put(chatId, filter);
                editMenu(chatId, messageId, "Заполните фильтр", keyboardFactory.editFilterMenu(filter, false));

            } else if (callData.equals(InlineButton.TRACK_NEW.getData())) {
                tempTrackerValues.put(chatId, new Tracker(chatId));
                showInputLinkMenu(chatId);
                userState.put(chatId, "WAITING_FOR_LINK");
                userMessageIdTemp.put(chatId, messageId);

            } else if (callData.equals(InlineButton.SET_FILTER_WORD.getData())) {
                inputEditFilterValue(chatId, "Введите ключевое слово:").ifPresent(message -> {
                    userState.put(chatId, "WAITING_FOR_WORD");
                    userMessageIdTemp.put(chatId, messageId);
                    userStateMessage.put(chatId, new DeleteMessage(String.valueOf(chatId), message.getMessageId()));
                });

            } else if (callData.equals(InlineButton.SET_FILTER_CATEGORY.getData())) {
                inputEditFilterValue(chatId, "Введите категорию:").ifPresent(message -> {
                    userState.put(chatId, "WAITING_FOR_CATEGORY");
                    userMessageIdTemp.put(chatId, messageId);
                    userStateMessage.put(chatId, new DeleteMessage(String.valueOf(chatId), message.getMessageId()));
                });

            } else if (callData.equals(InlineButton.SET_FILTER_PRICE.getData())) {
                inputEditFilterValue(chatId, "Введите стоимость:").ifPresent(message -> {
                    userState.put(chatId, "WAITING_FOR_PRICE");
                    userMessageIdTemp.put(chatId, messageId);
                    userStateMessage.put(chatId, new DeleteMessage(String.valueOf(chatId), message.getMessageId()));
                });

            } else if (callData.equals(InlineButton.SAVE_FILTER.getData())
                    || callData.equals(InlineButton.SAVE_EDIT_FILTER.getData())) {
                try {
                    searchFilterRepository.save(tempFilterValues.get(chatId));
                    tempFilterValues.remove(chatId);
                    answerCallback(update.getCallbackQuery().getId(), "Фильтр сохранен");
                    List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                    editMenu(chatId, messageId, "Список фильтров", keyboardFactory.getFiltersMenu(userPages.get(chatId), searchFilters));
                } catch (RuntimeException e) {
                    log.error("Ошибка сохранения фильтра пользователя {}: ", chatId, e);
                    answerCallback(update.getCallbackQuery().getId(), "❌ Ошибка БД. Попробуйте позже");
                }

            } else if (callData.startsWith("deleteFilter_")) {
                try {
                    long filterId = Long.parseLong(callData.split("_")[1]);
                    searchFilterRepository.deleteById(filterId);
                    answerCallback(update.getCallbackQuery().getId(), "Фильтр удален");
                    List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                    editMenu(chatId, messageId, "Список фильтров", keyboardFactory.getFiltersMenu(userPages.getOrDefault(chatId, 0), searchFilters));
                } catch (RuntimeException e) {
                    log.error("Ошибка удаления фильтра пользователя {}: ", chatId, e);
                    answerCallback(update.getCallbackQuery().getId(), "❌ Ошибка БД. Попробуйте позже");
                }
            } else if (callData.equals(InlineButton.DELETE_TRACKER.getData())) {
                try {
                    trackerRepository.deleteTrackerByChatIdAndId(chatId, tempTrackerValues.get(chatId).getId());
                    answerCallback(update.getCallbackQuery().getId(), "Ссылка удалена");
                    menuTrack(chatId);
                } catch (RuntimeException e) {
                    log.error("Ошибка удаления трекера пользователя {}: ", chatId, e);
                    answerCallback(update.getCallbackQuery().getId(), "❌ Ошибка БД. Попробуйте позже");
                }
            } else if (callData.equals(InlineButton.CANCEL.getData())) {
                userState.remove(chatId);
                executeMessage(new DeleteMessage(String.valueOf(chatId), callback.getMessage().getMessageId()));
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
        executeMessage(edit);
    }

    public void sendMessageText(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения пользователю {}: ", chatId, e);
        }
    }

    private void menuFilters(long chatId) {
        List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
        InlineKeyboardMarkup inlineKeyboardMarkup = keyboardFactory.getFiltersMenu(userPages.get(chatId), searchFilters);
        sendMessage(SendMessage.builder()
                .chatId(chatId)
                .text("Список фильтров")
                .replyMarkup(inlineKeyboardMarkup)
                .build());
    }

    private void menuAllGoods(long chatId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = keyboardFactory.getGoodsMenu();
        sendMessage(SendMessage.builder()
                .chatId(chatId)
                .text("Выберите файл")
                .replyMarkup(inlineKeyboardMarkup)
                .build());
    }

    private void menuTrack(long chatId) {
        List<Tracker> trackerList = trackerRepository.findAllByChatId(chatId);
        InlineKeyboardMarkup inlineKeyboardMarkup = keyboardFactory.getTrackersMenu(userPages.get(chatId), trackerList);
        sendMessage(SendMessage.builder()
                .chatId(chatId)
                .text("Список отслеживаемых товаров")
                .replyMarkup(inlineKeyboardMarkup)
                .build());
    }

    private void menuReply(long chatId) {
        executeMessage(keyboardFactory.mainMenuReply(chatId));
    }

    private void showInputLinkMenu(long chatId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = keyboardFactory.inputLinkMenu();
        sendMessage(SendMessage.builder()
                .chatId(chatId)
                .text("Введите ссылку")
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("HTML")
                .build());
    }

    private Optional<Message> inputEditFilterValue(long chatId, String text) {
        InlineKeyboardMarkup inlineKeyboardMarkup = keyboardFactory.inputLinkMenu();
        return sendMessage(SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("HTML")
                .build());
    }

    private void executeDocumentSend(SendDocument sendDocument) {
        try {
            telegramClient.execute(sendDocument);
            log.info("✅ Документ успешно отправлен в чат {}", sendDocument.getChatId());
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка при выполнении отправки документа: {}", e.getMessage());
        }
    }

    private void sendFile(long chatId, Optional<File> file, String caption) {
        file.ifPresentOrElse(f -> {
                    SendDocument sendDocument = SendDocument.builder()
                            .chatId(chatId)
                            .document(new InputFile(f))
                            .caption(caption)
                            .build();
                    executeDocumentSend(sendDocument);
                },
                () -> sendMessageText(chatId, "К сожалению, Ваш файл еще не сформирован"));
    }

    private void answerCallback(String queryId, String text) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(queryId)
                .text(text)
                .showAlert(false)
                .build();
        executeMessage(answer);
    }

    private <T extends Serializable> void executeMessage(BotApiMethod<T> method) {
        try {
            telegramClient.execute(method);
        } catch (TelegramApiException e) {
            log.error("Ошибка при выполнении метода Telegram Api: {}", e.getMessage(), e);
        }
    }

    private Optional<Message> sendMessage(SendMessage message) {
        try {
            return Optional.ofNullable(telegramClient.execute(message));
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void sendPhoto (SendPhoto sendPhoto) {
        try {
            telegramClient.execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправки фото: {}", e.getMessage());
        }
    }

    private boolean isUserNotAuthorized(long chatId) {
        return !activeUsersCache.contains(chatId);
    }

    private void registerUser(long chatId, String name) {
        userTelegramRepository.save(new UserTelegram(chatId, name));
        activeUsersCache.add(chatId);
    }

    private void deleteMessage(long chatId, Integer messageId) {
        DeleteMessage deleteMessage = new DeleteMessage(String.valueOf(chatId), messageId);
        try {
            telegramClient.execute(deleteMessage);
        } catch (TelegramApiException e) {
            log.error("Не удалось удалить карточку товара: {}", e.getMessage());
        }
    }
}
