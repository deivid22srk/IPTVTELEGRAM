package com.example.iptvfinder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class BotSettingsActivity extends AppCompatActivity {

    private EditText botTokenEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bot_settings);

        botTokenEditText = findViewById(R.id.bot_token_edit_text);
        Button saveTokenButton = findViewById(R.id.save_token_button);

        saveTokenButton.setOnClickListener(v -> {
            String botToken = botTokenEditText.getText().toString().trim();
            if (!botToken.isEmpty()) {
                saveBotToken(botToken);
                Toast.makeText(this, "Bot token saved!", Toast.LENGTH_SHORT).show();
                startService(new Intent(this, IPTVCollectorService.class));
                finish(); // Optional: close this activity after saving the token
            } else {
                Toast.makeText(this, "Please enter a bot token.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveBotToken(String token) {
        SharedPreferences sharedPref = getSharedPreferences("IPTVFinderPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("bot_token", token);
        editor.apply();
    }
}
