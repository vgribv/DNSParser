package ru.vgribv.parser.bot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import ru.vgribv.parser.entity.SearchFilter;
import ru.vgribv.parser.entity.Tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class KeyboardFactory {

    public static SendMessage mainMenuReply(long chatId) {
        KeyboardRow row1 = new KeyboardRow();
        row1.add("Все товары");
        row1.add("Фильтры");
        KeyboardRow row2 = new KeyboardRow();
        row2.add("Отследить");
        row2.add("Парсинг");

        return SendMessage.builder()
                .chatId(chatId)
                .text("Меню")
                .replyMarkup(getButtons(row1, row2))
                .build();
    }

    public static InlineKeyboardMarkup getFiltersMenu(int currentPage, List<SearchFilter> searchFilterlist) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        int pageSize = 6;
        int start = pageSize * currentPage;
        int end = Math.min(start + pageSize, searchFilterlist.size());
        List<SearchFilter> filters = searchFilterlist.subList(start, end);
        for (SearchFilter filter : filters) {
            String keyword = formatField(filter.getKeyword(), "Без названия", "");
            String category = formatField(filter.getCategory(), "Все категории", "");
            String price = formatField(filter.getMaxPrice(), "Без лимита", "₽");
            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                    .text("⚙️ " + keyword + " | " + category + " | " + price)
                    .callbackData("editFilter_" + filter.getId())
                    .build();
            rows.add(new InlineKeyboardRow(btn));
        }

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (currentPage > 0) {
            navRow.add(InlineKeyboardButton.builder().text("⬅️").callbackData("filterPage_" + (currentPage - 1)).build());
        }
        if (end < searchFilterlist.size()) {
            navRow.add(InlineKeyboardButton.builder().text("➡️").callbackData("filterPage_" + (currentPage + 1)).build());
        }

        if (!navRow.isEmpty()) rows.add(new InlineKeyboardRow(navRow));

        rows.add(new InlineKeyboardRow(InlineButton.NEW_FILTER.build()));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();

    }

    public static InlineKeyboardMarkup getTrackersMenu(int currentPage, List<Tracker> trackerList) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        int pageSize = 6;
        int start = pageSize * currentPage;
        int end = Math.min(start + pageSize, trackerList.size());
        List<Tracker> trackers = trackerList.subList(start, end);
        for (Tracker tracker : trackers) {
            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                    .text("\uD83D\uDCE6 " + tracker.getName())
                    .callbackData("editTracker_" + tracker.getLink())
                    .build();
            rows.add(new InlineKeyboardRow(btn));
        }

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (currentPage > 0) {
            navRow.add(InlineKeyboardButton.builder().text("⬅️").callbackData("trackerPage_" + (currentPage - 1)).build());
        }
        if (end < trackerList.size()) {
            navRow.add(InlineKeyboardButton.builder().text("➡️").callbackData("trackerPage_" + (currentPage + 1)).build());
        }

        if (!navRow.isEmpty()) rows.add(new InlineKeyboardRow(navRow));

        rows.add(new InlineKeyboardRow(InlineButton.TRACK_NEW.build()));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup inputFilterValue() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(InlineButton.CANCEL.build())
                )).build();
    }

    public static InlineKeyboardMarkup editFilterMenu(SearchFilter searchFilter, boolean isEdit) {
        InlineKeyboardRow inlineKeyboardRow;
        if (isEdit) {
            inlineKeyboardRow = new InlineKeyboardRow(InlineButton.SAVE_EDIT_FILTER.build(),
                    InlineKeyboardButton.builder().text("Удалить").callbackData("deleteFilter_" + searchFilter.getId()).build());
        } else {
            inlineKeyboardRow = new InlineKeyboardRow(InlineButton.SAVE_EDIT_FILTER.build());
        }
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(dynamicFilterButton(formatField(searchFilter.getKeyword(), "Без названия", ""),
                                InlineButton.SET_FILTER_WORD.getData())),
                        new InlineKeyboardRow(dynamicFilterButton(formatField(searchFilter.getCategory(), "Без категории", ""),
                                InlineButton.SET_FILTER_CATEGORY.getData())),
                        new InlineKeyboardRow(dynamicFilterButton(formatField(searchFilter.getMaxPrice(), "Без лимита", "₽"),
                                InlineButton.SET_FILTER_PRICE.getData())),
                        inlineKeyboardRow)
                ).build();
    }

    public static InlineKeyboardMarkup editTrackerMenu(String link) {
        InlineKeyboardButton linkButton = InlineKeyboardButton.builder()
                .text("🔗 Перейти к товару")
                .url("https://www.dns-shop.ru/catalog/markdown/" + link)
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(linkButton),
                        new InlineKeyboardRow(InlineButton.DELETE_TRACKER.build()),
                        new InlineKeyboardRow(InlineButton.BACK_TRACK.build())
                ))
                .build();
    }

    public static InlineKeyboardMarkup inputLinkMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(InlineButton.CANCEL.build())
                ))
                .build();
    }

    public static InlineKeyboardMarkup getGoodsMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(InlineButton.GOODS_TXT.build()),
                        new InlineKeyboardRow(InlineButton.GOODS_PERCENT_TXT.build()),
                        new InlineKeyboardRow(InlineButton.GOODS_LOG.build())
                ))
                .build();
    }

    private static ReplyKeyboardMarkup getButtons(KeyboardRow... row) {
        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(row)) // Добавляем список рядов
                .resizeKeyboard(true)
                .oneTimeKeyboard(false)
                .selective(true)
                .build();
    }

    private static InlineKeyboardButton dynamicFilterButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private static String formatField(Object value, String placeholder, String suffix) {
        return Optional.ofNullable(value).filter(v -> !v.toString().isEmpty())
                .map(v -> v + suffix).
                orElse(placeholder);
    }


}