package ru.vgribv.parser.bot.callback;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface Callback {
    void execute (Update update);
    String getCallbackName();
}
