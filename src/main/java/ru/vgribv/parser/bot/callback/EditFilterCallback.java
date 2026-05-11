package ru.vgribv.parser.bot.callback;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.vgribv.parser.bot.KeyboardFactory;
import ru.vgribv.parser.bot.TelegramBot;
import ru.vgribv.parser.entity.SearchFilter;
import ru.vgribv.parser.repository.SearchFilterRepository;

@Component
public class EditFilterCallback implements Callback {

    private final SearchFilterRepository searchFilterRepository;
    private final TelegramBot telegramBot;
    private final KeyboardFactory keyboardFactory;

    public EditFilterCallback(SearchFilterRepository searchFilterRepository,
                              @Lazy TelegramBot telegramBot, KeyboardFactory keyboardFactory) {
        this.searchFilterRepository = searchFilterRepository;
        this.telegramBot = telegramBot;
        this.keyboardFactory = keyboardFactory;
    }

    @Override
    public void execute(Update update) {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        long messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callData = update.getCallbackQuery().getData().split("_")[1];

        SearchFilter searchFilter = searchFilterRepository
                .getFirstById(Long.parseLong(callData));

        telegramBot.editMenu(chatId, (int) messageId, "Редактировать фильтр",
                keyboardFactory.editFilterMenu(searchFilter, true));
        telegramBot.getTempFilterValues().put(chatId, searchFilter);
    }

    @Override
    public String getCallbackName() {
        return "editFilter";
    }
}
