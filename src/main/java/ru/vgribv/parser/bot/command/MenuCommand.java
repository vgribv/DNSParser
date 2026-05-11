package ru.vgribv.parser.bot.command;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.vgribv.parser.bot.TelegramBot;

@Component
public class MenuCommand implements Command{

    private final TelegramBot telegramBot;

    public MenuCommand(@Lazy TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @Override
    public void execute(Update update) {
        telegramBot.menuReply(update.getMessage().getChatId());
    }

    @Override
    public String getCommandName() {
        return "/menu";
    }
}
