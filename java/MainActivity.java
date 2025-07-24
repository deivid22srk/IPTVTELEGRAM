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
import com.google.android.material.progressindicator.LinearProgressIndicator;
// import androidx.appcompat.widget.Toolbar; // Removido para evitar conflito

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
    private LinearProgressIndicator progressIndicator;
    // private Toolbar toolbar; // Removido para evitar conflito

    private List<String> combos = new ArrayList<>();
    private List<String> proxies = new ArrayList<>();
    private List<Hit> hits = new ArrayList<>();
    private boolean isScanning = false;
    private LoadCombosTask loadCombosTask;

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
        // Removido Toolbar para evitar conflito com ActionBar
        // toolbar = findViewById(R.id.toolbar);
        // setSupportActionBar(toolbar);
        
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
        progressIndicator = findViewById(R.id.progressIndicator);
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
        // Cancelar tarefa anterior se existir
        if (loadCombosTask != null && !loadCombosTask.isCancelled()) {
            loadCombosTask.cancel(true);
            // Limpar arquivo temporário da tarefa anterior se existir
            File oldTempFile = loadCombosTask.getTempComboFile();
            if (oldTempFile != null && oldTempFile.exists()) {
                oldTempFile.delete();
            }
        }
        
        // Força garbage collection antes de carregar novo arquivo
        System.gc();
        
        // Criar nova tarefa
        loadCombosTask = new LoadCombosTask();
        loadCombosTask.execute(uri);
    }

    private void loadProxiesFromFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            proxies.clear();
            
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    proxies.add(line.trim());
                }
            }
            
            reader.close();
            proxyFileEditText.setText("Proxies carregados: " + proxies.size());
            Toast.makeText(this, "Proxies carregados: " + proxies.size(), Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Toast.makeText(this, "Erro ao carregar proxies: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        
        // Se há um arquivo temporário (arquivo grande), passar o caminho
        if (loadCombosTask != null && loadCombosTask.getTempComboFile() != null && loadCombosTask.getTempComboFile().exists()) {
            serviceIntent.putExtra("temp_combo_file", loadCombosTask.getTempComboFile().getAbsolutePath());
        }
        
        startForegroundService(serviceIntent);

        isScanning = true;
        startScanButton.setEnabled(false);
        stopScanButton.setEnabled(true);
        statusTextView.setText(getString(R.string.status_starting));
        progressIndicator.setVisibility(View.VISIBLE);
        progressIndicator.setIndeterminate(true);
    }

    private void stopScan() {
        Intent serviceIntent = new Intent(this, ScanService.class);
        stopService(serviceIntent);
        
        isScanning = false;
        startScanButton.setEnabled(true);
        stopScanButton.setEnabled(false);
        statusTextView.setText(getString(R.string.status_stopped, hits.size(), 0));
        progressIndicator.setVisibility(View.GONE);
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
        });
    }

    @Override
    public void onStatusUpdate(int hitCount, int failCount) {
        runOnUiThread(() -> {
            statusTextView.setText(getString(R.string.status_scanning, hitCount, failCount));
        });
    }
    
    @Override
    public void onProgressUpdate(int processed, int total, int botsActive) {
        runOnUiThread(() -> {
            if (total > 0 && progressIndicator != null) {
                progressIndicator.setIndeterminate(false);
                progressIndicator.setMax(total);
                progressIndicator.setProgress(processed);
                
                int percentage = (processed * 100) / total;
                String progressText = String.format("🤖 %d bots ativos | Progresso: %d%% (%d/%d)", 
                    botsActive, percentage, processed, total);
                
                // Update status to include progress
                String currentStatus = statusTextView.getText().toString();
                if (currentStatus.contains("|")) {
                    String[] parts = currentStatus.split("\\|");
                    if (parts.length >= 2) {
                        statusTextView.setText(parts[0] + "| " + parts[1] + "\n" + progressText);
                    }
                } else {
                    statusTextView.setText(currentStatus + "\n" + progressText);
                }
            }
        });
    }

    @Override
    public void onScanFinished(int hitCount, int failCount) {
        runOnUiThread(() -> {
            isScanning = false;
            startScanButton.setEnabled(true);
            stopScanButton.setEnabled(false);
            statusTextView.setText(getString(R.string.status_stopped, hitCount, failCount));
            progressIndicator.setVisibility(View.GONE);
            checkStartButtonState();
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
        
        // Limpar arquivo temporário se existir
        if (loadCombosTask != null) {
            File tempFile = loadCombosTask.getTempComboFile();
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
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

    private class LoadCombosTask extends android.os.AsyncTask<Uri, Integer, Boolean> {
        private androidx.appcompat.app.AlertDialog dialog;
        private Uri[] uris;
        private TextView progressText;
        private LinearProgressIndicator progressBar;
        private long totalLines = 0;
        private long currentLine = 0;
        private File tempComboFile;
        private static final int MAX_MEMORY_COMBOS = 10000; // Limit to prevent OOM

        @Override
        protected void onPreExecute() {
            // Create custom progress dialog with modern design
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_progress, null);
            progressText = dialogView.findViewById(R.id.progressText);
            progressBar = dialogView.findViewById(R.id.progressBar);
            
            if (progressText != null) {
                progressText.setText("Preparando para carregar arquivo...");
            }
            if (progressBar != null) {
                progressBar.setIndeterminate(true);
            }
            
            builder.setView(dialogView);
            builder.setCancelable(false);
            dialog = builder.create();
            dialog.show();
        }

        @Override
        protected Boolean doInBackground(Uri... uris) {
            this.uris = uris;
            
            try {
                // Create temporary file for large combo processing
                tempComboFile = new File(getCacheDir(), "temp_combos.txt");
                
                // First pass: count total lines for progress calculation
                publishProgress(0); // Indicate we're counting lines
                
                InputStream inputStream = getContentResolver().openInputStream(uris[0]);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream), 16384); // Larger buffer
                
                // Count lines efficiently without storing in memory
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(":")) {
                        totalLines++;
                    }
                }
                reader.close();
                
                publishProgress(1); // Indicate we're starting to process
                
                // Determine processing strategy based on file size
                boolean useMemoryProcessing = totalLines <= MAX_MEMORY_COMBOS;
                
                if (useMemoryProcessing) {
                    return loadToMemory();
                } else {
                    return processLargeFile();
                }
                
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            } catch (OutOfMemoryError e) {
                // Fallback to file processing if we run out of memory
                System.gc(); // Force garbage collection
                try {
                    return processLargeFile();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
        }
        
        private boolean loadToMemory() throws IOException {
            // Small files - load to memory (original behavior)
            List<String> combos = new ArrayList<>();
            
            InputStream inputStream = getContentResolver().openInputStream(uris[0]);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream), 16384);
            
            currentLine = 0;
            int batchSize = 500; // Smaller batches to prevent OOM
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(":")) {
                    combos.add(line.trim());
                    currentLine++;
                    
                    // Update progress every batch
                    if (currentLine % batchSize == 0) {
                        publishProgress(2); // Regular progress update
                        
                        // Check memory usage
                        Runtime runtime = Runtime.getRuntime();
                        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                        long maxMemory = runtime.maxMemory();
                        
                        if (usedMemory > maxMemory * 0.8) { // If using more than 80% memory
                            System.gc(); // Force garbage collection
                            
                            // If still high memory usage, switch to file processing
                            if ((runtime.totalMemory() - runtime.freeMemory()) > maxMemory * 0.7) {
                                reader.close();
                                return processLargeFile();
                            }
                        }
                    }
                }
            }
            
            reader.close();
            MainActivity.this.combos = combos;
            return true;
        }
        
        private boolean processLargeFile() throws IOException {
            // Large files - copy to temp file and process directly from file
            publishProgress(3); // Indicate file processing mode
            
            FileOutputStream fos = new FileOutputStream(tempComboFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            
            InputStream inputStream = getContentResolver().openInputStream(uris[0]);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream), 16384);
            
            currentLine = 0;
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains(":")) {
                    osw.write(line + "\n");
                    currentLine++;
                    
                    if (currentLine % 1000 == 0) {
                        publishProgress(4); // File processing progress
                        osw.flush(); // Ensure data is written
                    }
                }
            }
            
            reader.close();
            osw.close();
            
            // Set combos to empty list - will be processed directly from file
            MainActivity.this.combos = new ArrayList<>();
            return true;
        }
        
        @Override
        protected void onProgressUpdate(Integer... values) {
            int stage = values[0];
            
            if (progressText != null && progressBar != null) {
                switch (stage) {
                    case 0:
                        progressText.setText("Analisando tamanho do arquivo...");
                        progressBar.setIndeterminate(true);
                        break;
                    case 1:
                        String processingMode = totalLines > MAX_MEMORY_COMBOS ? "arquivo" : "memória";
                        progressText.setText(String.format("Iniciando carregamento de %d combos (modo %s)...", 
                            totalLines, processingMode));
                        progressBar.setIndeterminate(false);
                        progressBar.setMax((int)totalLines);
                        progressBar.setProgress(0);
                        break;
                    case 2:
                        int progress = (int)((currentLine * 100) / totalLines);
                        progressText.setText(String.format("Carregando na memória: %d/%d (%d%%)", 
                            currentLine, totalLines, progress));
                        progressBar.setProgress((int)currentLine);
                        break;
                    case 3:
                        progressText.setText("Arquivo muito grande! Mudando para modo de processamento otimizado...");
                        progressBar.setIndeterminate(true);
                        break;
                    case 4:
                        int fileProgress = (int)((currentLine * 100) / totalLines);
                        progressText.setText(String.format("Processando arquivo: %d/%d (%d%%)", 
                            currentLine, totalLines, fileProgress));
                        progressBar.setIndeterminate(false);
                        progressBar.setMax((int)totalLines);
                        progressBar.setProgress((int)currentLine);
                        break;
                }
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            
            if (success) {
                String message;
                if (totalLines > MAX_MEMORY_COMBOS) {
                    message = String.format("📁 %d combos (modo arquivo otimizado)", totalLines);
                } else {
                    message = String.format("📁 %d combos carregados", combos.size());
                }
                
                fileEditText.setText(message);
                fileEditText.setTag(uris[0]);
                checkStartButtonState();
                
                // Show success message
                Toast.makeText(MainActivity.this, 
                    "✅ Arquivo processado com sucesso: " + totalLines + " combos", 
                    Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, 
                    "❌ Erro ao processar arquivo. Tente um arquivo menor.", 
                    Toast.LENGTH_LONG).show();
            }
        }
        
        public File getTempComboFile() {
            return tempComboFile;
        }
    }
}

