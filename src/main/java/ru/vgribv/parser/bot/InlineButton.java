package ru.vgribv.parser.bot;

import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

public enum InlineButton {
    GOODS_PERCENT_TXT("Все товары по максимальной скидке", "goodsDiscountPercent"),
    GOODS_LOG("Запись изменений", "goodsLog"),
    GOODS_TXT("Все товары по порядку", "goodsTxt"),
    ALL_GOODS("Все товары", "allGoods"),
    BACK_TRACK("Назад", "backTrack"),
    FILTERS("Фильтры", "filters"),
    TRACK("Отслеживать", "track"),
    TRACK_NEW("Отследить новый товар", "newTracker"),
    TRACK_GOODS("Отслеживаемые товары", "trackGoods"),
    PARSE("Принудительный парсинг", "parse"),
    NEW_FILTER("Новый фильтр", "newFilter"),
    SET_FILTER_WORD("Ключевое слово", "setFilterWord"),
    SET_FILTER_CATEGORY("Категория", "setFilterCategory"),
    SET_FILTER_PRICE("Цена","setFilterPrice"),
    SAVE_FILTER("Сохранить", "saveFilter"),
    SAVE_EDIT_FILTER("Сохранить", "editFilter"),
    DELETE_FILTER("Удалить", "deleteFilter"),
    DELETE_TRACKER("Удалить", "deleteTracker"),
    PREV_PAGE("Назад", "prevPage"),
    NEXT_PAGE("Вперед", "nextPage"),
    CANCEL("Отмена", "cancel");


    private final String text;
    @Getter
    private final String data;

    InlineButton(String text, String data) {
        this.text = text;
        this.data = data;
    }

    public InlineKeyboardButton build() {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(data)
                .build();
    }
}
