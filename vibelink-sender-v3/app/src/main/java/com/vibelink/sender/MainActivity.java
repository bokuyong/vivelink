package com.vibelink.sender;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);
        FrameLayout fragmentContainer = findViewById(R.id.fragmentContainer);

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
            Insets bar = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int minBottom = (int) (48 * getResources().getDisplayMetrics().density);
            int bottomMargin = Math.max(bar.bottom, minBottom);

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.bottomMargin = bottomMargin;
            v.setLayoutParams(lp);

            return WindowInsetsCompat.CONSUMED;
        });

        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer, (v, insets) -> {
            Insets bar = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int minBottom = (int) (48 * getResources().getDisplayMetrics().density);
            int bottom = Math.max(bar.bottom, minBottom);
            v.setPadding(bar.left, bar.top, bar.right, 64 + bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            showFragment(new DeviceFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_device) {
                showFragment(new DeviceFragment());
            } else if (id == R.id.nav_ai) {
                showFragment(new AiFragment());
            } else if (id == R.id.nav_history) {
                showFragment(new HistoryFragment());
            } else if (id == R.id.nav_settings) {
                showFragment(new SettingsFragment());
            }

            return true;
        });
    }

    // Replace fragment in container
    private void showFragment(Fragment fragment) {
        activeFragment = fragment;
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.replace(R.id.fragmentContainer, fragment);
        tx.commit();
    }
}
