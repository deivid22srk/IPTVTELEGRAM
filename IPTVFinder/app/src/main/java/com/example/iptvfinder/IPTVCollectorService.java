package com.example.iptvfinder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.OkHttpClient;
import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import okhttp3.Request;
import okhttp3.Response;

public class IPTVCollectorService extends Service {

    private static final String CHANNEL_ID = "IPTVCollectorServiceChannel";
    private static final String TAG = "IPTVCollectorService";
    private SimpleHttpServer server;
    private List<String> validUrls = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            server = new SimpleHttpServer(8080);
        } catch (IOException e) {
            e.printStackTrace();
        }

        SharedPreferences sharedPref = getSharedPreferences("IPTVFinderPrefs", Context.MODE_PRIVATE);
        String botToken = sharedPref.getString("bot_token", null);

        if (botToken != null) {
            try {
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                botsApi.registerBot(new TelegramBot(botToken, this));
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("IPTV Collector")
                .setContentText("Listening for IPTV lists...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(1, notification);

        return START_NOT_STICKY;
    }

    private void updateNotification(String text) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("IPTV Collector")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1, notification);
    }

    public void validateUrl(String url) {
        new Thread(() -> {
            // This is a simplified validation logic.
            // A more robust implementation would parse the URL to extract username and password
            // and then construct the player_api.php URL.
            if (!url.contains("get.php")) {
                Log.d(TAG, "Invalid URL format: " + url);
                return;
            }

            String playerApiUrl = url.replace("get.php", "player_api.php");

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(playerApiUrl).build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    if (json.contains("\"auth\":1") && json.contains("\"status\":\"Active\"")) {
                        Log.d(TAG, "Valid URL: " + url);
                        validUrls.add(url);
                        server.setValidUrls(validUrls);
                        updateNotification("Found " + validUrls.size() + " valid lists.");
                    } else {
                        Log.d(TAG, "Invalid URL (auth failed): " + url);
                    }
                } else {
                    Log.d(TAG, "Invalid URL (request failed): " + url);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error validating URL: " + url, e);
            }
        }).start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "IPTV Collector Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
    }
}
