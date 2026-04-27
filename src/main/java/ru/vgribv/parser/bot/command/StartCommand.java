package ru.vgribv.parser.bot.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.vgribv.parser.bot.TelegramBot;
import ru.vgribv.parser.service.UserService;


@Component
@Slf4j
public class StartCommand implements Command {

    private final UserService userService;
    private final TelegramBot telegramBot;

    public StartCommand(UserService userService, @Lazy TelegramBot telegramBot) {
        this.userService = userService;
        this.telegramBot = telegramBot;
    }

    @Override
    public void execute(Update update) {
        Long chatId = update.getMessage().getChatId();
        boolean isNewUser = userService.isUserNotAuthorized(chatId);

        if (isNewUser) {
            User from = update.getMessage().getFrom();
            userService.registerUser(chatId, from.getFirstName());
            log.info("Зарегистрирован новый пользователь: {}", from.getFirstName());
        }
        telegramBot.menuReply(chatId);
    }

    @Override
    public String getCommandName() {
        return "/start";
    }
}
