package ru.vgribv.parser.bot.command;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.vgribv.parser.bot.KeyboardFactory;
import ru.vgribv.parser.bot.TelegramBot;
import ru.vgribv.parser.entity.SearchFilter;
import ru.vgribv.parser.repository.SearchFilterRepository;

import java.util.List;

@Component
public class FiltersCommand implements Command{

    private final TelegramBot telegramBot;
    private final KeyboardFactory keyboardFactory;
    private final SearchFilterRepository searchFilterRepository;

    public FiltersCommand(@Lazy TelegramBot telegramBot, KeyboardFactory keyboardFactory,
                          SearchFilterRepository searchFilterRepository) {
        this.telegramBot = telegramBot;
        this.keyboardFactory = keyboardFactory;
        this.searchFilterRepository = searchFilterRepository;
    }

    @Override
    public void execute(Update update) {
        long chatId = update.getMessage().getChatId();
        telegramBot.getUserPages().put(chatId, 0);
        menuFilters(chatId);
        telegramBot.executeMessage(getDeleteMessage(update));
    }

    @Override
    public String getCommandName() {
        return "Фильтры";
    }

    private DeleteMessage getDeleteMessage(Update update) {
        return DeleteMessage.builder()
                .chatId(update.getMessage().getChatId())
                .messageId(update.getMessage().getMessageId())
                .build();
    }

    private void menuFilters(long chatId) {
        List<SearchFilter> searchFilters = searchFilterRepository.getAllByChatId(chatId);
        InlineKeyboardMarkup inlineKeyboardMarkup = keyboardFactory.getFiltersMenu(telegramBot.getUserPages().get(chatId), searchFilters);
        telegramBot.sendMessage(SendMessage.builder()
                .chatId(chatId)
                .text("Список фильтров")
                .replyMarkup(inlineKeyboardMarkup)
                .build());
    }
}
