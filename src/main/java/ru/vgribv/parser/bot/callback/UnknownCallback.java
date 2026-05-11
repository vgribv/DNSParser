package ru.vgribv.parser.bot.callback;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.vgribv.parser.bot.TelegramBot;

@Component
public class UnknownCallback implements Callback{

    private final TelegramBot telegramBot;

    public UnknownCallback(@Lazy TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @Override
    public void execute(Update update) {
        String callbackQueryId = update.getCallbackQuery().getId();
        AnswerCallbackQuery answer = new AnswerCallbackQuery(callbackQueryId);
        answer.setText("Эта кнопка больше не активна");
        telegramBot.executeMessage(answer);
    }

    @Override
    public String getCallbackName() {
        return "unknownCallback";
    }
}
