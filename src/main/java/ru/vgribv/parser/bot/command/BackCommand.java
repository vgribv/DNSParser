package ru.vgribv.parser.bot.command;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.vgribv.parser.bot.TelegramBot;

@Component
public class BackCommand implements Command{

    private final TelegramBot telegramBot;

    public BackCommand(@Lazy TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @Override
    public void execute(Update update) {
        long chatId = update.getMessage().getChatId();
        telegramBot.menuReply(chatId);
        telegramBot.executeMessage(getDeleteMessage(update));
    }

    @Override
    public String getCommandName() {
        return "Назад";
    }

    private DeleteMessage getDeleteMessage(Update update) {
        return DeleteMessage.builder()
                .chatId(update.getMessage().getChatId())
                .messageId(update.getMessage().getMessageId())
                .build();
    }

}
