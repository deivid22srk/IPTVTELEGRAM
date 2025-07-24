package com.Scanner.IPTV;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> directoryPickerLauncher;
    private SwitchMaterial telegramSwitch;
    private TextInputEditText telegramBotTokenEditText;
    private TextInputEditText telegramGroupIdEditText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        MaterialButton changeFolderButton = findViewById(R.id.changeFolderButton);
        telegramSwitch = findViewById(R.id.telegramSwitch);
        telegramBotTokenEditText = findViewById(R.id.telegramBotTokenEditText);
        telegramGroupIdEditText = findViewById(R.id.telegramGroupIdEditText);

        loadSettings();

        directoryPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    prefs.edit().putString("hits_directory", uri.toString()).apply();
                    Toast.makeText(this, "Pasta de hits atualizada!", Toast.LENGTH_SHORT).show();
                }
            }
        );

        changeFolderButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            directoryPickerLauncher.launch(intent);
        });

        telegramSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("telegram_enabled", isChecked).apply();
            updateTelegramInputsState(isChecked);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveTelegramSettings();
    }

    private void loadSettings() {
        boolean telegramEnabled = prefs.getBoolean("telegram_enabled", true);
        telegramSwitch.setChecked(telegramEnabled);
        telegramBotTokenEditText.setText(prefs.getString("telegram_bot_token", "8245169261:AAHTUygk3X99DtXysRwkPcjM7cYo0-FNpcQ"));
        telegramGroupIdEditText.setText(prefs.getString("telegram_group_id", "-1002710854837"));
        updateTelegramInputsState(telegramEnabled);
    }

    private void saveTelegramSettings() {
        prefs.edit()
            .putString("telegram_bot_token", telegramBotTokenEditText.getText().toString())
            .putString("telegram_group_id", telegramGroupIdEditText.getText().toString())
            .apply();
    }

    private void updateTelegramInputsState(boolean enabled) {
        telegramBotTokenEditText.setEnabled(enabled);
        telegramGroupIdEditText.setEnabled(enabled);
    }
}
