package com.example.iptvfinder;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button sendCodeButton = findViewById(R.id.send_code_button);
        sendCodeButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, VerifyCodeActivity.class);
            startActivity(intent);
        });
    }
}
