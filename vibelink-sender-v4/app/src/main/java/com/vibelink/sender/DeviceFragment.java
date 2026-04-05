package com.vibelink.sender;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vibelink.sender.network.StreamServer;
import com.vibelink.sender.service.ScreenCaptureService;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Enumeration;

// Device tab fragment - streaming controls
public class DeviceFragment extends Fragment {

    private TextView tvStatus;
    private TextView tvIpAddress;
    private Button btnStart;
    private Button btnStop;
    private View dotMirror;
    private boolean capturing = false;

    private final ActivityResultLauncher<Intent> projectionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            startCaptureService(result.getResultCode(), result.getData());
                        } else {
                            Toast.makeText(requireContext(), "화면 캡처 권한이 거부됨", Toast.LENGTH_SHORT).show();
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_device, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvStatus = view.findViewById(R.id.tvStatus);
        tvIpAddress = view.findViewById(R.id.tvIpAddress);
        btnStart = view.findViewById(R.id.btnStart);
        btnStop = view.findViewById(R.id.btnStop);
        dotMirror = view.findViewById(R.id.dotMirror);

        tvIpAddress.setText("IP: " + getLocalIpAddress() + ":" + StreamServer.DEFAULT_PORT);

        btnStart.setOnClickListener(v -> requestProjection());
        btnStop.setOnClickListener(v -> stopCapture());

        updateUi(false);
    }

    private void requestProjection() {
        MediaProjectionManager mgr =
                (MediaProjectionManager) requireContext().getSystemService(Activity.MEDIA_PROJECTION_SERVICE);
        projectionLauncher.launch(mgr.createScreenCaptureIntent());
    }

    private void startCaptureService(int resultCode, Intent data) {
        Intent svcIntent = new Intent(requireContext(), ScreenCaptureService.class);
        svcIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
        svcIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
        requireContext().startForegroundService(svcIntent);
        capturing = true;
        updateUi(true);
    }

    private void stopCapture() {
        requireContext().stopService(new Intent(requireContext(), ScreenCaptureService.class));
        capturing = false;
        updateUi(false);
    }

    private void updateUi(boolean isCapturing) {
        if (isCapturing) {
            tvStatus.setText("스트리밍 중 · 낮은 지연시간 30ms");
            dotMirror.setBackgroundResource(R.drawable.ic_dot_green);
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
        } else {
            tvStatus.setText("대기 중");
            dotMirror.setBackgroundResource(R.drawable.ic_dot_red);
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
        }
    }

    // Get device WiFi IP address
    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                Enumeration<java.net.InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {

                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            return "unknown";
        }

        return "unknown";
    }
}
