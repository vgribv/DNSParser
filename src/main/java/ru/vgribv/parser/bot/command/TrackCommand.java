package ru.vgribv.parser.bot.command;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.vgribv.parser.bot.TelegramBot;

@Component
public class TrackCommand implements Command{

    private final TelegramBot telegramBot;

    public TrackCommand(@Lazy TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @Override
    public void execute(Update update) {
        long chatId = update.getMessage().getChatId();
        telegramBot.getUserPages().put(chatId, 0);
        telegramBot.menuTrack(chatId);
        telegramBot.executeMessage(getDeleteMessage(update));
    }

    @Override
    public String getCommandName() {
        return "Отследить";
    }

    private DeleteMessage getDeleteMessage(Update update) {
        return DeleteMessage.builder()
                .chatId(update.getMessage().getChatId())
                .messageId(update.getMessage().getMessageId())
                .build();
    }
}
