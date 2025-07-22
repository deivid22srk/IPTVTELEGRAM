package com.example.iptvfinder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

public class GroupSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_selection);

        RecyclerView recyclerView = findViewById(R.id.groups_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<String> sampleGroups = new ArrayList<>();
        sampleGroups.add("IPTV Group 1");
        sampleGroups.add("Test Group");
        sampleGroups.add("Another IPTV Provider");

        GroupAdapter adapter = new GroupAdapter(sampleGroups);
        recyclerView.setAdapter(adapter);

        Button saveButton = findViewById(R.id.save_button);
        saveButton.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, IPTVCollectorService.class);
            startService(serviceIntent);
        });
    }
}
