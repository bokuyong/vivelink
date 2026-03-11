package com.vibelink.sender;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

// Settings tab fragment
public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "ai_prefs";
    private static final String KEY_CONTROLLER_IP = "controller_ip";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etControllerIp = view.findViewById(R.id.etControllerIp);
        loadSavedIp(etControllerIp);

        View btnOverlay = view.findViewById(R.id.btnTapOverlayPermission);
        TextView tvOverlayStatus = view.findViewById(R.id.tvOverlayStatus);
        if (btnOverlay != null && tvOverlayStatus != null) {
            updateOverlayStatus(tvOverlayStatus);
            btnOverlay.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    i.setData(Uri.parse("package:" + requireContext().getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
            });
        }

        etControllerIp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                saveIp(s.toString().trim());
            }
        });
    }

    private void loadSavedIp(EditText et) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_CONTROLLER_IP, "");
        if (!saved.isEmpty()) {
            et.setText(saved);
            et.setSelection(saved.length());
        }
    }

    private void saveIp(String ip) {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CONTROLLER_IP, ip)
                .apply();
    }

    private void updateOverlayStatus(TextView tv) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tv.setText(Settings.canDrawOverlays(requireContext()) ? "✓ 허용됨" : "설정에서 허용 필요");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        TextView tv = getView() != null ? getView().findViewById(R.id.tvOverlayStatus) : null;
        if (tv != null) updateOverlayStatus(tv);
    }
}
