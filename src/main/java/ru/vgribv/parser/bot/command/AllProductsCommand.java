package ru.vgribv.parser.bot.command;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.vgribv.parser.bot.KeyboardFactory;
import ru.vgribv.parser.bot.TelegramBot;

@Component
public class AllProductsCommand implements Command {

    private final TelegramBot telegramBot;
    private final KeyboardFactory keyboardFactory;

    public AllProductsCommand(@Lazy TelegramBot telegramBot, KeyboardFactory keyboardFactory) {
        this.telegramBot = telegramBot;
        this.keyboardFactory = keyboardFactory;
    }

    @Override
    public void execute(Update update) {
        long chatId = update.getMessage().getChatId();
        menuAllGoods(chatId);
        telegramBot.executeMessage(getDeleteMessage(update));
    }

    @Override
    public String getCommandName() {
        return "Все товары";
    }

    private void menuAllGoods(long chatId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = keyboardFactory.getGoodsMenu();
        telegramBot.sendMessage(SendMessage.builder()
                .chatId(chatId)
                .text("Выберите файл")
                .replyMarkup(inlineKeyboardMarkup)
                .build());
    }

    private DeleteMessage getDeleteMessage(Update update) {
        return DeleteMessage.builder()
                .chatId(update.getMessage().getChatId())
                .messageId(update.getMessage().getMessageId())
                .build();
    }
}


