package com.example.iptvfinder;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

public class TelegramBot extends TelegramLongPollingBot {

    private String botToken;

    public TelegramBot(String botToken) {
        this.botToken = botToken;
    }

    private IPTVCollectorService service;

    public TelegramBot(String botToken, IPTVCollectorService service) {
        this.botToken = botToken;
        this.service = service;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            // This is a simple regex to find URLs in the message text.
            // A more robust implementation would use a more comprehensive regex.
            String[] words = messageText.split("\\s+");
            for (String word : words) {
                if (word.startsWith("http://") || word.startsWith("https://")) {
                    service.validateUrl(word);
                }
            }
        }
    }

    @Override
    public String getBotUsername() {
        // You can return any name here, it doesn't matter for our use case.
        return "IPTVFinderBot";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}
