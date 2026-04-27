package ru.vgribv.parser.bot.command;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface Command {
    void execute (Update update);
    String getCommandName();
}
