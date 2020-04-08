/*
 * ******************************************************************
 * @title FLIR THERMAL SDK
 * @file MainActivity.java
 * @Author FLIR Systems AB
 *
 * @brief  Main UI of test application
 *
 * Copyright 2019:    FLIR Systems
 * ******************************************************************/
package com.samples.flironecamera;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.log.ThermalLog;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import androidx.appcompat.app.AppCompatActivity;

import static com.samples.flironecamera.FlirCameraContext.cameraHandler;
import static com.samples.flironecamera.FlirCameraContext.connectedIdentity;

/**
 * Sample application for scanning a FLIR ONE or a built in emulator
 * <p>
 * See the {@link CameraHandler} for how to preform discovery of a FLIR ONE camera, connecting to it and start streaming images
 * <p>
 * The MainActivity is primarily focused to "glue" different helper classes together and updating the UI components
 * <p/>
 * Please note, this is <b>NOT</b> production quality code, error handling has been kept to a minimum to keep the code as clear and concise as possible
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    //Handles Android permission for eg Network
    public PermissionHandler permissionHandler;

//    public Identity connectedIdentity = null;
    private TextView connectionStatus;
    private TextView discoveryStatus;

    /**
     * Show message on the screen
     */
    public interface ShowMessage {
        void show(String message);
    }
    private ShowMessage showMessage = message -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.DarkTheme);
        setContentView(R.layout.activity_main);

        ThermalSdkAndroid.init(getApplicationContext(), ThermalLog.LogLevel.WARNING);

        permissionHandler = new PermissionHandler(showMessage, MainActivity.this);
        cameraHandler = new CameraHandler();

        connectionStatus = findViewById(R.id.connection_status_text);
        discoveryStatus = findViewById(R.id.discovery_status);

        // Show Thermal Android SDK version
        TextView sdkVersionTextView = findViewById(R.id.sdk_version);
        String sdkVersionText = getString(R.string.sdk_version_text, ThermalSdkAndroid.getVersion());
        sdkVersionTextView.setText(sdkVersionText);

    }

    public void startDiscovery(View view) {
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
    }

    public void stopDiscovery(View view) {
        cameraHandler.stopDiscovery(discoveryStatusListener);
    }


    public void connectFlirOne(View view) {
//        connect(cameraHandler.getFlirOne());
    }

    public void connectSimulatorOne(View view) {
//        connect(cameraHandler.getCppEmulator());
    }

    public void connectSimulatorTwo(View view) {
        Intent intent = new Intent(getApplicationContext(), FlirEmulator.class);
        startActivity(intent);
//        connect(cameraHandler.getFlirOneEmulator());
    }

    public void disconnect(View view) {
        disconnect();
    }

    /**
     * Handle Android permission request response for Bluetooth permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult() called with: requestCode = [" + requestCode + "], permissions = [" + Arrays.toString(permissions) + "], grantResults = [" + Arrays.toString(grantResults) + "]");
        permissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Disconnect to a camera
     */
    private void disconnect() {
        updateConnectionText(connectedIdentity, "DISCONNECTING");
        connectedIdentity = null;
        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        new Thread(() -> {
            cameraHandler.disconnect();
            runOnUiThread(() -> updateConnectionText(null, "DISCONNECTED"));
        }).start();
    }

    /**
     * Update the UI text for connection status
     */
    private void updateConnectionText(Identity identity, String status) {
        String deviceId = identity != null ? " " + identity.deviceId : "";
        connectionStatus.setText("Connection Status:" + deviceId + " " + status);
    }

    /**
     * Callback for discovery status, using it to update UI
     */
    public CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {
            discoveryStatus.setText(R.string.discovery_status_discovering);
        }

        @Override
        public void stopped() {
            discoveryStatus.setText(R.string.discovery_status_text);
        }
    };

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    public ConnectionStatusListener connectionStatusListener = errorCode -> {
        Log.d(TAG, "onDisconnected errorCode:" + errorCode);

        runOnUiThread(() -> updateConnectionText(connectedIdentity, "DISCONNECTED"));
    };

    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(Identity identity) {
            Log.d(TAG, "onCameraFound identity:" + identity);
            runOnUiThread(() -> {
                if(identity.deviceId.contains("EMULATED FLIR ONE")){
                    findViewById(R.id.connect_s2).setVisibility(View.VISIBLE);
                } else if (identity.deviceId.contains("C++ Emulator")){
                    findViewById(R.id.connect_s1).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.connect_flir_one).setVisibility(View.VISIBLE);
                }
                cameraHandler.add(identity);
                MainActivity.this.showMessage.show("Camera Found: " + identity);
                stopDiscovery(null);
            });
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);

            runOnUiThread(() -> {
                stopDiscovery(null);
                MainActivity.this.showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
            });
        }

        @Override
        public void onDiscoveryFinished(CommunicationInterface communicationInterface) {
            Log.d(TAG, "onDiscoveryFinished: Discovery Finished");
        }
    };
}
