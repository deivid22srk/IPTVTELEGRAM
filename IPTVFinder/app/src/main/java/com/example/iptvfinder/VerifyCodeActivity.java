package com.example.iptvfinder;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class VerifyCodeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_code);

        Button verifyCodeButton = findViewById(R.id.verify_code_button);
        verifyCodeButton.setOnClickListener(v -> {
            Intent intent = new Intent(VerifyCodeActivity.this, GroupSelectionActivity.class);
            startActivity(intent);
        });
    }
}
