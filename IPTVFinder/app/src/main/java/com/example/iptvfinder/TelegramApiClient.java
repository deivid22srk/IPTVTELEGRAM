package com.example.iptvfinder;

import android.util.Log;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TelegramApiClient {

    private static final String TAG = "TelegramApiClient";
    private static final String API_BASE_URL = "https://api.telegram.org/bot";

    private final String botToken;
    private final OkHttpClient client;

    public TelegramApiClient(String botToken) {
        this.botToken = botToken;
        this.client = new OkHttpClient();
    }

    public String getUpdates(long offset) {
        String url = API_BASE_URL + botToken + "/getUpdates?offset=" + offset;
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                Log.e(TAG, "getUpdates request failed: " + response);
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting updates", e);
            return null;
        }
    }
}
