package com.example.freakyfriday;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.example.freakyfriday.ui.main.MainFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        if (savedInstanceState == null) {
//            getSupportFragmentManager().beginTransaction()
//                    .replace(R.id.container, MainFragment.newInstance())
//                    .commitNow();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, cardsFragment.newInstance())
                    .commitNow();
        }
    }
}
