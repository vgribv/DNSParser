package ru.vgribv.parser.bot;

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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.vgribv.parser.entity.*;
import ru.vgribv.parser.repository.ProductRepository;
import ru.vgribv.parser.repository.SearchFilterRepository;
import ru.vgribv.parser.repository.TrackerRepository;
import ru.vgribv.parser.repository.UserTelegramRepository;
import ru.vgribv.parser.service.ParserService;

import java.io.File;
import java.util.*;


@Component
@Slf4j
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

            if(userState.containsKey(chatId)) {
                String state = userState.get(chatId);
                DeleteMessage deleteMessage = new DeleteMessage(String.valueOf(chatId), update.getMessage().getMessageId());
                DeleteMessage deleteMessageTwo = userStateMessage.get(chatId);
                SearchFilter filter = tempFilterValues.get(chatId);
                Tracker tracker = tempTrackerValues.get(chatId);
                boolean flag = false;
                boolean flagRepeat = false;
                String message = messageText.toLowerCase();
                switch (state) {
                    case "WAITING_FOR_WORD" -> {
                        if (filter.getKeyword() != null && filter.getKeyword().equals(message)) {
                            flagRepeat = true;
                        } else {
                            filter.setKeyword(message);
                        }
                        flag = true;
                    }
                    case "WAITING_FOR_CATEGORY" -> {
                        if (filter.getCategory() != null && filter.getCategory().equals(message)) {
                            flagRepeat = true;
                        } else {
                            filter.setCategory(messageText.toLowerCase());
                        }
                        flag = true;
                    }
                    case "WAITING_FOR_PRICE" -> {
                        try {
                            int price = Integer.parseInt(messageText);
                            if (filter.getMaxPrice() != null && filter.getMaxPrice().equals(price)) {
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
                        if(trackerRepository.existsTrackerByChatIdAndLink(chatId, productId)) {
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
                    if (!flagRepeat) {
                        editMenu(chatId, userMessageIdTemp.get(chatId), sb, KeyboardFactory.setFilterMenu());
                    }
                }
                userMessageIdTemp.remove(chatId);
                userState.remove(chatId);
                userStateMessage.remove(chatId);
                for (int i = 0; true; i++) {
                    try {
                        execute(deleteMessage);
                        if (deleteMessageTwo != null) {
                            execute(deleteMessageTwo);
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
                case "/start", "/menu" -> {
                    UserTelegram userTelegram = userTelegramRepository.findByChatId(chatId).orElseGet(() -> new UserTelegram(chatId));
                    userTelegram.setActive(true);
                    userTelegramRepository.save(userTelegram);
                    menuReply(chatId);
                }
                case "Все товары" -> {
                    menuAllGoods(chatId);
                    execute(deleteMessageReply);
                }
                case "Фильтры" -> {
                    userPages.put(chatId, 0);
                    menuFilters(chatId);
                    execute(deleteMessageReply);
                }
                case "Отследить" -> {
                    userPages.put(chatId, 0);
                    menuTrack(chatId);
                    execute(deleteMessageReply);
                }
                case "Парсинг" ->
                    parserService.manualRun().ifPresentOrElse(_ ->
                                    sendMessageText(chatId, "✅ Парсинг успешно запущен."),
                            () -> sendMessageText(chatId, "⏳ Ошибка: Задача уже выполняется."));
                case "Назад" -> {
                    menuReply(chatId);
                    execute(deleteMessageReply);
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
                editMenu(chatId, messageId, "Редактировать фильтр",
                        KeyboardFactory.editFilterMenu(filterId));
                tempFilterValues.put(chatId, searchFilter);
            } else if (callData.startsWith("filterPage_")) {
                List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                int currentPage = Integer.parseInt(callData.split("_")[1]);
                userPages.put(chatId, currentPage);
                editMenu(chatId, messageId, "Список фильтров",
                        KeyboardFactory.getFiltersMenu(currentPage, searchFilters));
            } else if (callData.startsWith("trackerPage_")) {
                int currentPage = Integer.parseInt(callData.split("_")[1]);
                userPages.put(chatId, currentPage);
                List<Tracker> trackerList = trackerRepository.getAllByChatId(chatId);
                editMenu(chatId, messageId, "Список отслеживаемых товаров",
                        KeyboardFactory.getTrackersMenu(currentPage, trackerList));
            } else if (callData.startsWith("editTracker_")) {
                String link = String.valueOf(callData.split("_")[1]);
                StringBuilder sb = new StringBuilder();
                productRepository.findProductByLinkId(link).ifPresentOrElse(product -> {
                            Category category = product.getCategory();
                            String categoryName = (category != null) ? category.getName() : "Общая категория";
                            sb.append("<b>").append(product.getName()).append("</b>\n")
                                    .append("💰 Цена: <s>").append(product.getFullPrice()).append("</s> ")
                                    .append(product.getDiscountPrice()).append(" руб.\n")
                                    .append("\uD83D\uDCCA Категория: ").append(categoryName).append("\n");
                            },
                        () -> sendMessageText(chatId, "Ошибка. Попробуйте еще раз."));

                Tracker tracker = trackerRepository.findFirstByChatIdAndLink(chatId, link);
                editMenu(chatId, messageId, sb.toString(), KeyboardFactory.editTrackerMenu(link));
                tempTrackerValues.put(chatId, tracker);
            } else if (callData.equals(InlineButton.FILTERS.getData())) {
                List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                editMenu(chatId, messageId, "Настройка фильтров",
                        KeyboardFactory.getFiltersMenu(userPages.get(chatId), searchFilters));
            } else if (callData.equals(InlineButton.BACK_TRACK.getData())) {
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
                showInputLinkMenu(chatId);
                userState.put(chatId, "WAITING_FOR_LINK");
                userMessageIdTemp.put(chatId, messageId);
            } else if (callData.equals(InlineButton.SET_FILTER_WORD.getData())) {
                sendMessageText(chatId, "Введите ключевое слово:").ifPresent(prevMessageId -> {
                    userState.put(chatId, "WAITING_FOR_WORD");
                    userMessageIdTemp.put(chatId, messageId);
                    userStateMessage.put(chatId, new DeleteMessage(String.valueOf(chatId), prevMessageId));
                });
            } else if (callData.equals(InlineButton.SET_FILTER_CATEGORY.getData())) {
                sendMessageText(chatId, "Введите категорию:").ifPresent(prevMessageId -> {
                    userState.put(chatId, "WAITING_FOR_CATEGORY");
                    userMessageIdTemp.put(chatId, messageId);
                    userStateMessage.put(chatId, new DeleteMessage(String.valueOf(chatId), prevMessageId));
                });
            } else if (callData.equals(InlineButton.SET_FILTER_PRICE.getData())) {
                sendMessageText(chatId, "Введите максимальную цену:").ifPresent(prevMessageId -> {
                    userState.put(chatId, "WAITING_FOR_PRICE");
                    userMessageIdTemp.put(chatId, messageId);
                    userStateMessage.put(chatId, new DeleteMessage(String.valueOf(chatId), prevMessageId));
                });
            } else if (callData.equals(InlineButton.SAVE_FILTER.getData())
                    || callData.equals(InlineButton.SAVE_EDIT_FILTER.getData())) {
                try {
                    searchFilterRepository.save(tempFilterValues.get(chatId));
                    tempFilterValues.remove(chatId);
                    answerCallback(update.getCallbackQuery().getId(), "Фильтр сохранен");
                    List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
                    editMenu(chatId, messageId, "Список фильтров", KeyboardFactory.getFiltersMenu(userPages.get(chatId), searchFilters));
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
                    editMenu(chatId, messageId, "Список фильтров", KeyboardFactory.getFiltersMenu(userPages.getOrDefault(chatId, 0), searchFilters));
                } catch (RuntimeException e){
                    log.error("Ошибка удаления фильтра пользователя {}: ", chatId, e);
                    answerCallback(update.getCallbackQuery().getId(), "❌ Ошибка БД. Попробуйте позже");
                }
            } else if (callData.equals(InlineButton.DELETE_TRACKER.getData())) {
                try {
                    trackerRepository.deleteTrackerByChatIdAndId(chatId, tempTrackerValues.get(chatId).getId());
                    List<Tracker> trackerList = trackerRepository.getAllByChatId(chatId);
                    answerCallback(update.getCallbackQuery().getId(), "Ссылка удалена");
                    editMenu(chatId, messageId, "Список отслеживаемых товаров", KeyboardFactory.getTrackersMenu(userPages.get(chatId), trackerList));
                }
                catch (RuntimeException e) {
                    log.error("Ошибка удаления трекера пользователя {}: ", chatId, e);
                    answerCallback(update.getCallbackQuery().getId(), "❌ Ошибка БД. Попробуйте позже");
                }
            } else if (callData.equals(InlineButton.CANCEL.getData())) {
                userState.remove(chatId);
                execute(new DeleteMessage(String.valueOf(chatId), callback.getMessage().getMessageId()));
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
        execute(edit);
    }

    public Optional<Integer> sendMessageText(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .build();
        try {
            Message sentMessage = telegramClient.execute(message);
            return Optional.of(sentMessage.getMessageId());
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения пользователю {}: ", chatId, e);
            return Optional.empty();
        }
    }

    private void menuFilters (long chatId) {
        List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
        InlineKeyboardMarkup inlineKeyboardMarkup = KeyboardFactory.getFiltersMenu(userPages.get(chatId),searchFilters);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Список фильтров")
                .replyMarkup(inlineKeyboardMarkup)
                .build();
        execute(sendMessage);
    }

    private void menuAllGoods(long chatId){
        InlineKeyboardMarkup inlineKeyboardMarkup = KeyboardFactory.getGoodsMenu();
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Выберите файл")
                .replyMarkup(inlineKeyboardMarkup)
                .build();
        execute(sendMessage);
    }

    private void menuTrack (long chatId) {
        List<Tracker> trackerList = trackerRepository.findAllByChatId(chatId);
        InlineKeyboardMarkup inlineKeyboardMarkup = KeyboardFactory.getTrackersMenu(userPages.get(chatId), trackerList);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Список отслеживаемых товаров")
                .replyMarkup(inlineKeyboardMarkup)
                .build();
        execute(sendMessage);
    }

    private void menuReply(long chatId) {
        execute(KeyboardFactory.mainMenuReply(chatId));
    }

    private void showInputLinkMenu (long chatId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = KeyboardFactory.inputLinkMenu();
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Введите ссылку")
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("HTML")
                .build();
        execute(sendMessage);
    }

    private void sendTextFile(long chatId, String filePath) {
        File  file = new File(filePath);
        if (!file.exists() || file.isDirectory()) {
            log.error("Попытка отправить несуществующий файл пользователю {}: {}", chatId, filePath);
            sendMessageText(chatId, "Извините, ваш файл еще не сформирован.");
            return;
        }
        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(file))
                .caption("📄 Ваши результаты поиска")
                .build();
        try {
            telegramClient.execute(sendDocument);
            log.info("Файл {} успешно отправлен пользователю {}", file.getName(), chatId);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке текстового файла для пользователя {}: ", chatId, e);
        }
    }

    private void answerCallback(String queryId, String text) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(queryId)
                .text(text)
                .showAlert(false)
                .build();
        execute(answer);
    }

    private void execute(BotApiMethod<?> method) {
        try {
            telegramClient.execute(method);
        } catch (TelegramApiException e){
            log.error("Ошибка при выполнении метода Telegram Api: {}", e.getMessage(), e);
        }
    }
}
