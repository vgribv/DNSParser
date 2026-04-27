package ru.vgribv.parser.bot.command;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.vgribv.parser.bot.TelegramBot;

@Component
public class UnknownCommand implements Command {
    private final TelegramBot telegramBot;

    public UnknownCommand(@Lazy TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @Override
    public void execute(Update update) {
        telegramBot.sendMessageText(update.getMessage().getChatId(), "Неизвестная команда...");
    }

    @Override
    public String getCommandName() {
        return "/unknown";
    }
}