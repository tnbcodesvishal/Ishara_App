package com.google.mediapipe.examples.handlandmarker;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.mediapipe.examples.handlandmarker.fragment.CameraFragment;
import com.google.mediapipe.examples.handlandmarker.fragment.SignTranslateFragment;
import com.google.mediapipe.examples.handlandmarker.fragment.home;
import com.google.mediapipe.examples.handlandmarker.fragment.learnisl;
import com.google.mediapipe.examples.handlandmarker.fragment.profilefragamnet;

public class MainMain extends AppCompatActivity {

    BottomNavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main_main);

        if (!InternetUtil.isConnected(this)) {
            InternetUtil.showNoInternetDialog(this);
        }

        navigationView = findViewById(R.id.bnv_bottom);

        loadFragment(new home());


        navigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;

            switch (item.getItemId()) {
                case R.id.homeee:
                    fragment = new home();
                    break;

                case R.id.Sing:
                    fragment = new CameraFragment();
                    break;


                case R.id.speach:
                    fragment = new SignTranslateFragment();
                    break;

                case R.id.learnisl:
                    fragment = new learnisl();
                    break;

            }

            if (fragment != null) loadFragment(fragment);
            return true;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main, fragment)
                .commit();
    }
}