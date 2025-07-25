package com.Scanner.IPTV;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements ScanService.ScanListener {

    private LinearLayout panelsContainer;
    private TextInputEditText fileEditText;
    private TextInputEditText proxyUrlEditText;
    private TextInputEditText proxyFileEditText;
    private AutoCompleteTextView speedSpinner;
    private RadioGroup proxyRadioGroup;
    private TextInputLayout proxyUrlInputLayout;
    private TextInputLayout proxyFileInputLayout;
    private MaterialButton startScanButton;
    private MaterialButton stopScanButton;
    private MaterialButton copyAllButton;
    private TextView statusTextView;
    private LinearLayout hitsContainer;

    private List<String> combos = new ArrayList<>();
    private List<String> proxies = new ArrayList<>();
    private List<Hit> hits = new ArrayList<>();
    
    // Constantes para limites de memória
    private static final int MAX_COMBOS_DEFAULT = 50000;
    private static final int MAX_PROXIES_DEFAULT = 10000;
    private static final int BATCH_SIZE_DEFAULT = 1000;
    private boolean isScanning = false;

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> proxyFilePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        requestPermissions();
        restoreUiState();
        addPanelInput();
        
        // Registrar como listener do serviço
        ScanService.setListener(this);
    }

    private void initViews() {
        panelsContainer = findViewById(R.id.panelsContainer);
        fileEditText = findViewById(R.id.fileEditText);
        proxyUrlEditText = findViewById(R.id.proxyUrlEditText);
        proxyFileEditText = findViewById(R.id.proxyFileEditText);
        speedSpinner = findViewById(R.id.speedSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.speed_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speedSpinner.setAdapter(adapter);
        speedSpinner.setText(adapter.getItem(0), false);
        proxyRadioGroup = findViewById(R.id.proxyRadioGroup);
        proxyUrlInputLayout = findViewById(R.id.proxyUrlInputLayout);
        proxyFileInputLayout = findViewById(R.id.proxyFileInputLayout);
        startScanButton = findViewById(R.id.startScanButton);
        stopScanButton = findViewById(R.id.stopScanButton);
        copyAllButton = findViewById(R.id.copyAllButton);
        statusTextView = findViewById(R.id.statusTextView);
        hitsContainer = findViewById(R.id.hitsContainer);
    }

    private void setupListeners() {
        // File picker launchers
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    final int takeFlags = result.getData().getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (takeFlags != 0) {
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    }
                    loadCombosFromFile(uri);
                    checkStartButtonState();
                }
            }
        );

        proxyFilePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    loadProxiesFromFile(uri);
                }
            }
        );

        // Add panel button
        MaterialButton addPanelButton = findViewById(R.id.addPanelButton);
        addPanelButton.setOnClickListener(v -> addPanelInput());

        // File input click listener
        fileEditText.setOnClickListener(v -> openFilePicker());

        // Proxy file input click listener
        proxyFileEditText.setOnClickListener(v -> openProxyFilePicker());

        // Proxy radio group listener
        proxyRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioNone) {
                proxyUrlInputLayout.setVisibility(View.GONE);
                proxyFileInputLayout.setVisibility(View.GONE);
                proxies.clear();
            } else if (checkedId == R.id.radioGithub) {
                proxyUrlInputLayout.setVisibility(View.VISIBLE);
                proxyFileInputLayout.setVisibility(View.GONE);
            } else if (checkedId == R.id.radioFile) {
                proxyUrlInputLayout.setVisibility(View.GONE);
                proxyFileInputLayout.setVisibility(View.VISIBLE);
            }
        });

        // Start scan button
        startScanButton.setOnClickListener(v -> startScan());

        // Stop scan button
        stopScanButton.setOnClickListener(v -> stopScan());

        // Copy all button
        copyAllButton.setOnClickListener(v -> copyAllHits());
    }

    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.POST_NOTIFICATIONS
        };

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 1);
                break;
            }
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("text/plain");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        filePickerLauncher.launch(intent);
    }

    private void openProxyFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/plain");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        proxyFilePickerLauncher.launch(intent);
    }

    private void loadCombosFromFile(Uri uri) {
        // Verifica memória disponível antes de carregar
        if (!isMemoryAvailable()) {
            Toast.makeText(this, "Memória insuficiente. Feche outros aplicativos e tente novamente.", Toast.LENGTH_LONG).show();
            return;
        }
        
        new LoadCombosTask().execute(uri);
    }
    
    /**
     * Verifica se há memória suficiente disponível
     */
    private boolean isMemoryAvailable() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long availableMemory = maxMemory - usedMemory;
        
        // Requer pelo menos 50MB livres
        return availableMemory > (50 * 1024 * 1024);
    }
    
    /**
     * Força limpeza de memória
     */
    private void forceGarbageCollection() {
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.gc();
    }
    
    /**
     * Libera recursos desnecessários para economizar memória
     */
    private void freeUnusedResources() {
        // Limpa hits antigos se houver muitos
        if (hits.size() > 1000) {
            int toRemove = hits.size() - 500;
            for (int i = 0; i < toRemove; i++) {
                hits.remove(0);
                if (hitsContainer.getChildCount() > 0) {
                    hitsContainer.removeViewAt(0);
                }
            }
        }
        forceGarbageCollection();
    }

    private void loadProxiesFromFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Erro ao abrir arquivo de proxies", Toast.LENGTH_SHORT).show();
                return;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream), 8192);
            String line;
            proxies.clear();
            
            int count = 0;
            while ((line = reader.readLine()) != null && count < MAX_PROXIES_DEFAULT) {
                line = line.trim();
                if (!line.isEmpty()) {
                    proxies.add(line);
                    count++;
                }
            }
            
            reader.close();
            
            String message;
            if (count >= MAX_PROXIES_DEFAULT) {
                message = "Proxies carregados: " + proxies.size() + " (limite atingido)";
                Toast.makeText(this, "Limite de " + MAX_PROXIES_DEFAULT + " proxies atingido.", Toast.LENGTH_LONG).show();
            } else {
                message = "Proxies carregados: " + proxies.size();
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
            
            proxyFileEditText.setText(message);
            
            // Força garbage collection
            System.gc();
            
        } catch (IOException e) {
            Toast.makeText(this, "Erro ao carregar proxies: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (OutOfMemoryError e) {
            System.gc();
            Toast.makeText(this, "Arquivo de proxies muito grande! Carregados apenas " + proxies.size() + " proxies.", Toast.LENGTH_LONG).show();
        }
    }

    private void addPanelInput() {
        addPanelInput(null);
    }

    private void checkStartButtonState() {
        boolean canStart = getPanels().size() > 0
                          && !combos.isEmpty()
                          && !isScanning;
        startScanButton.setEnabled(canStart);
    }

    private ArrayList<String> getPanels() {
        ArrayList<String> panels = new ArrayList<>();
        for (int i = 0; i < panelsContainer.getChildCount(); i++) {
            View panelInputView = panelsContainer.getChildAt(i);
            TextInputEditText editText = panelInputView.findViewById(R.id.panelEditText);
            String panel = editText.getText().toString().trim();
            if (!panel.isEmpty()) {
                panels.add(panel);
            }
        }
        return panels;
    }

    private void startScan() {
        ArrayList<String> panels = getPanels();
        if (panels.isEmpty() || combos.isEmpty()) {
            Toast.makeText(this, "Preencha pelo menos um painel e selecione um arquivo de combos", Toast.LENGTH_SHORT).show();
            return;
        }

        // Verificar proxy se necessário
        int checkedRadioId = proxyRadioGroup.getCheckedRadioButtonId();
        if (checkedRadioId == R.id.radioGithub) {
            String proxyUrl = proxyUrlEditText.getText().toString().trim();
            if (proxyUrl.isEmpty()) {
                Toast.makeText(this, "Informe a URL do arquivo de proxies", Toast.LENGTH_SHORT).show();
                return;
            }
            // TODO: Carregar proxies do GitHub
        } else if (checkedRadioId == R.id.radioFile && proxies.isEmpty()) {
            Toast.makeText(this, "Selecione um arquivo de proxies", Toast.LENGTH_SHORT).show();
            return;
        }

        // Obter velocidade
        String speedStr = speedSpinner.getText().toString();
        if (speedStr.isEmpty()) {
            Toast.makeText(this, "Selecione uma velocidade", Toast.LENGTH_SHORT).show();
            return;
        }
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(speedStr);
        int speed = 10; // Default speed
        if (matcher.find()) {
            speed = Integer.parseInt(matcher.group(0));
        }

        // Limpar hits anteriores
        hits.clear();
        hitsContainer.removeAllViews();
        copyAllButton.setVisibility(View.GONE);

        // Iniciar serviço
        Intent serviceIntent = new Intent(this, ScanService.class);
        serviceIntent.putStringArrayListExtra("panels", panels);
        serviceIntent.putExtra("combo_file_uri", fileEditText.getTag().toString());
        serviceIntent.putExtra("speed", speed);
        
        startForegroundService(serviceIntent);

        isScanning = true;
        startScanButton.setEnabled(false);
        stopScanButton.setEnabled(true);
        statusTextView.setText(getString(R.string.status_starting));
    }

    private void stopScan() {
        Intent serviceIntent = new Intent(this, ScanService.class);
        stopService(serviceIntent);
        
        isScanning = false;
        startScanButton.setEnabled(true);
        stopScanButton.setEnabled(false);
        statusTextView.setText(getString(R.string.status_stopped, hits.size(), 0));
    }

    private String saveListToFile(String fileName, List<String> list) {
        File file = new File(getCacheDir(), fileName);
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos)) {
            for (String item : list) {
                osw.write(item + "\n");
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void copyAllHits() {
        if (hits.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (Hit hit : hits) {
            sb.append(hit.getFormattedText()).append("\n\n");
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Hits", sb.toString());
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this, "Todos os hits copiados!", Toast.LENGTH_SHORT).show();
    }

    private void addHitCard(Hit hit) {
        View cardView = LayoutInflater.from(this).inflate(R.layout.hit_card_layout, hitsContainer, false);
        
        TextView contentTextView = cardView.findViewById(R.id.hitContentTextView);
        MaterialButton copyButton = cardView.findViewById(R.id.copyButton);
        
        contentTextView.setText(hit.getFormattedText());
        
        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Hit", hit.getFormattedText());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Hit copiado!", Toast.LENGTH_SHORT).show();
        });
        
        hitsContainer.addView(cardView);
        copyAllButton.setVisibility(View.VISIBLE);
    }

    // Implementação da interface ScanListener
    @Override
    public void onHitFound(Hit hit) {
        runOnUiThread(() -> {
            hits.add(hit);
            addHitCard(hit);
            
            // Limpa recursos automaticamente se necessário
            if (hits.size() % 100 == 0 && !isMemoryAvailable()) {
                freeUnusedResources();
            }
        });
    }

    @Override
    public void onStatusUpdate(int hitCount, int failCount) {
        runOnUiThread(() -> {
            statusTextView.setText(getString(R.string.status_scanning, hitCount, failCount));
        });
    }

    @Override
    public void onScanFinished(int hitCount, int failCount) {
        runOnUiThread(() -> {
            isScanning = false;
            startScanButton.setEnabled(true);
            stopScanButton.setEnabled(false);
            statusTextView.setText(getString(R.string.status_stopped, hitCount, failCount));
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveUiState();
    }

    private void saveUiState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();

        ArrayList<String> panels = getPanels();
        editor.putStringSet("panels", new java.util.HashSet<>(panels));

        if (fileEditText.getTag() != null) {
            editor.putString("combo_file_uri", fileEditText.getTag().toString());
        }

        editor.putString("speed", speedSpinner.getText().toString());

        editor.apply();
    }

    private void restoreUiState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        java.util.Set<String> panels = prefs.getStringSet("panels", null);
        if (panels != null) {
            panelsContainer.removeAllViews();
            for (String panel : panels) {
                addPanelInput(panel);
            }
        }

        String comboFileUriString = prefs.getString("combo_file_uri", null);
        if (comboFileUriString != null) {
            Uri comboFileUri = Uri.parse(comboFileUriString);
            loadCombosFromFile(comboFileUri);
        }

        String speed = prefs.getString("speed", null);
        if (speed != null) {
            speedSpinner.setText(speed, false);
        }
    }

    private void addPanelInput(String text) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View panelInputView = inflater.inflate(R.layout.panel_input_layout, panelsContainer, false);
        TextInputEditText editText = panelInputView.findViewById(R.id.panelEditText);
        editText.setText(text);

        MaterialButton removeButton = panelInputView.findViewById(R.id.removePanelButton);
        removeButton.setOnClickListener(v -> {
            panelsContainer.removeView(panelInputView);
            checkStartButtonState();
        });

        editText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkStartButtonState();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        panelsContainer.addView(panelInputView);
    }

    protected void onDestroy() {
        super.onDestroy();
        ScanService.setListener(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class LoadCombosTask extends android.os.AsyncTask<Uri, Integer, List<String>> {
        private androidx.appcompat.app.AlertDialog dialog;
        private Uri[] uris;
        private TextView progressText;
        // Usa as constantes da classe principal

        @Override
        protected void onPreExecute() {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_progress, null);
            progressText = new TextView(MainActivity.this);
            progressText.setText("Iniciando carregamento...");
            progressText.setPadding(20, 20, 20, 20);
            
            LinearLayout layout = new LinearLayout(MainActivity.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(dialogView);
            layout.addView(progressText);
            
            builder.setView(layout);
            builder.setCancelable(false);
            dialog = builder.create();
            dialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (progressText != null) {
                progressText.setText("Carregados: " + values[0] + " combos");
            }
        }

        @Override
        protected List<String> doInBackground(Uri... uris) {
            this.uris = uris;
            List<String> combos = new ArrayList<>();
            
            try {
                InputStream inputStream = getContentResolver().openInputStream(uris[0]);
                if (inputStream == null) {
                    return combos;
                }
                
                // Verifica tamanho disponível aproximado
                int available = inputStream.available();
                if (available > 100 * 1024 * 1024) { // 100MB
                    // Arquivo muito grande, avisa o usuário
                    publishProgress(-1);
                }
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream), 8192);
                String line;
                int count = 0;
                int batchCount = 0;
                
                while ((line = reader.readLine()) != null && count < MAX_COMBOS_DEFAULT) {
                    line = line.trim();
                    if (!line.isEmpty() && line.contains(":")) {
                        combos.add(line);
                        count++;
                        batchCount++;
                        
                        // Atualiza progresso a cada lote
                        if (batchCount >= BATCH_SIZE_DEFAULT) {
                            publishProgress(count);
                            batchCount = 0;
                            
                            // Pequena pausa para liberar CPU
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                
                reader.close();
                
                // Força garbage collection para liberar memória
                System.gc();
                
            } catch (IOException e) {
                e.printStackTrace();
            } catch (OutOfMemoryError e) {
                // Em caso de falta de memória, tenta liberar e retornar o que foi carregado
                System.gc();
                Toast.makeText(MainActivity.this, "Arquivo muito grande! Carregados apenas " + combos.size() + " combos.", Toast.LENGTH_LONG).show();
            }
            
            return combos;
        }

        @Override
        protected void onPostExecute(List<String> result) {
            if (dialog != null) {
                dialog.dismiss();
            }
            
            MainActivity.this.combos = result;
            
            String message;
            if (result.size() >= MAX_COMBOS_DEFAULT) {
                message = "Arquivo carregado: " + result.size() + " combos (limite atingido)";
                Toast.makeText(MainActivity.this, "Limite de " + MAX_COMBOS_DEFAULT + " combos atingido. Para melhor performance, considere usar um arquivo menor.", Toast.LENGTH_LONG).show();
            } else {
                message = "Arquivo carregado: " + result.size() + " combos";
            }
            
            fileEditText.setText(message);
            fileEditText.setTag(uris[0]);
            checkStartButtonState();
            
            // Força garbage collection final
            System.gc();
        }
    }
}

