package ru.vgribv.parser.bot.command;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.vgribv.parser.bot.TelegramBot;
import ru.vgribv.parser.service.ParserService;

@Component
public class ParseCommand implements Command{

    private final TelegramBot telegramBot;
    private final ParserService parserService;

    public ParseCommand(@Lazy TelegramBot telegramBot, ParserService parserService) {
        this.telegramBot = telegramBot;
        this.parserService = parserService;
    }

    @Override
    public void execute(Update update) {
        long chatId = update.getMessage().getChatId();
        parserService.manualRun().ifPresentOrElse(p ->
                        telegramBot.sendMessageText(chatId, "✅ Парсинг успешно запущен."),
                () -> telegramBot.sendMessageText(chatId, "⏳ Ошибка: Задача уже выполняется."));
    }

    @Override
    public String getCommandName() {
        return "Парсинг";
    }
}
