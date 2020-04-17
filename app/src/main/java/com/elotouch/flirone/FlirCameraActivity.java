package com.elotouch.flirone;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.image.TemperatureUnit;
import com.flir.thermalsdk.image.fusion.FusionMode;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

import static com.elotouch.flirone.FlirCameraApplication.cameraHandler;
import static com.elotouch.flirone.FlirCameraApplication.connectedCameraIdentity;

public class FlirCameraActivity extends AppCompatActivity {
    public static final String CONNECTING = "CONNECTING";
    private static final String TAG = "FlirCameraActivity";
    public static final String CONNECTED = "CONNECTED";
    public static final String DISCONNECTED = "DISCONNECTED";
    public static final String DISCONNECTING = "DISCONNECTING";

    public MainActivity.ShowMessage showMessage = message -> Toast.makeText(FlirCameraActivity.this, message, Toast.LENGTH_SHORT).show();

    public UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    public LinkedBlockingQueue<BitmapFrameBuffer> framesBuffer = new LinkedBlockingQueue<>(21);

    public static FusionMode curr_fusion_mode = FusionMode.THERMAL_ONLY;

    private TextView connectionStatus;

    private ImageView msxImage;
    private ImageView photoImage;
    private static Menu menu;

    ScaleGestureDetector mScaleGestureDetector;

    public static double left = 0;
    public static double top = 0;
    public static double width;
    public static double height;

    @SuppressLint("StaticFieldLeak")
    private static FlirCameraActivity instance;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.flir_emulator_main);
        msxImage = findViewById(R.id.msx_image);
        photoImage = findViewById(R.id.photo_image);
        connectionStatus = findViewById(R.id.connection_status_text);

        width = 200;
        height = 200;
        mScaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        // Show Thermal Android SDK version
        TextView sdkVersionTextView = findViewById(R.id.sdk_version);
        String sdkVersionText = getString(R.string.sdk_version_text, ThermalSdkAndroid.getVersion());
        sdkVersionTextView.setText(sdkVersionText);
        instance = this;

        // TODO: Set default behavior if getIntent == null: Log error. (not that it ever should, but it will fix the lint error)
        switch (getIntent().getAction()) {
            case MainActivity.ACTION_START_FLIR_ONE:
                connectCamera(cameraHandler.getFlirOne());
                break;
            case MainActivity.ACTION_START_SIMULATOR_ONE:
                connectCamera(cameraHandler.getCppEmulator());
                break;
            case MainActivity.ACTION_START_SIMULATOR_TWO:
                connectCamera(cameraHandler.getFlirOneEmulator());
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CalibrationHandler.calibrationButtonHidden = true;
    }

    public static FlirCameraActivity getInstance(){
        return instance;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        FlirCameraActivity.menu = menu;
        getMenuInflater().inflate(R.menu.toolbar1, menu);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_toolbar_back);
        updateTitle();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.toolbar_switch:
                switchCamera();
                break;
            case R.id.toolbar_shuffle:
                if(msxImage.getVisibility() == View.VISIBLE){
                    switchFilter();
                    updateTitle();
                } else{
                    Toast.makeText(getApplicationContext(),"In normal camera mode! Switch camera mode first.",Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.toolbar_temperature:
                if(CameraHandler.getTemperatureUnit() == TemperatureUnit.KELVIN) {
                    CameraHandler.setTemperatureUnit(TemperatureUnit.CELSIUS);
                } else if(CameraHandler.getTemperatureUnit() == TemperatureUnit.CELSIUS){
                    CameraHandler.setTemperatureUnit(TemperatureUnit.FAHRENHEIT);
                } else if(CameraHandler.getTemperatureUnit() == TemperatureUnit.FAHRENHEIT){
                    CameraHandler.setTemperatureUnit(TemperatureUnit.KELVIN);
                }
                updateTitle();
                break;
            case R.id.calibrate:
                if(connectionStatus.getText().toString().contains(CONNECTED)){
                    Intent intent = new Intent(getApplicationContext(), CalibrateActivity.class);
                    intent.setAction(MainActivity.ACTION_START_CALIBRATION);
                    startActivity(intent);
                }
            case R.id.toolbar_reset:
                if(msxImage != null && photoImage != null){
                    width = CameraHandler.thermal_width/2.0;
                    height = width;
                    left = CameraHandler.thermal_width/2 - width/2;
                    top = CameraHandler.thermal_height/2 - height/2;
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    int touchx = -1;
    int touchy = -1;


    public void saveLog() {
        StringBuilder msgLog = new StringBuilder();
        Date date;

        if (CameraHandler.tempLog.size() != 0) {
            for (Map.Entry<Long, String> entry : CameraHandler.tempLog.entrySet()) {
                date = new Date(entry.getKey());
                msgLog.append(date.toString()).append(": \t ").append(entry.getValue());
            }

        } else {
            msgLog.append("There are no logs recorded.");
        }

        FileWriter out = null;
        try {
            Date d = new Date(System.currentTimeMillis());
            String filename = d.toString().replace(":","").replace(" ","");
            String path = getApplicationContext().getExternalFilesDir("logs").getAbsolutePath();
            out = new FileWriter(new File(path, filename));
            Toast.makeText(getApplicationContext(), "File written to " + path + "/" + filename,Toast.LENGTH_SHORT).show();
            out.write(msgLog.toString());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        mScaleGestureDetector.onTouchEvent(event);

        if(msxImage != null && CameraHandler.thermal_width != -1 && CameraHandler.thermal_height != -1){

            int evx = (int)event.getX();
            int evy = (int)event.getY();
            if(touchx != -1)
                evx = touchx;
            if(touchy != -1)
                evy = touchy;

            int[] viewCoords = new int[2];
            msxImage.getLocationInWindow(viewCoords);
            int imageX = (evx - viewCoords[0]);
            int imageY = (evy - viewCoords[1]);

            float ratiow = (float) CameraHandler.thermal_width / msxImage.getWidth();
            float ratioh = (float) CameraHandler.thermal_height / msxImage.getHeight();

            if(evx - (width / 2)/ratiow > viewCoords[0]){
                if(evx + (width/2)/ratiow < viewCoords[0] + msxImage.getWidth()){
                    left = imageX * ratiow - width/2;
                } else{
                    left = CameraHandler.thermal_width - width;
                }
            } else{
                left = 0;
            }
            if(evy - (height / 2)/ratioh >viewCoords[1]){
                if(evy + (height/2)/ratioh < viewCoords[1] + msxImage.getHeight()){
                    top = imageY * ratioh - height/2;
                } else{
                    top = CameraHandler.thermal_height - height;
                }
            } else{
                top = 0;
            }
        }
        touchx = -1;
        touchy = -1;

        return super.onTouchEvent(event);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector){
            if(msxImage != null && photoImage!=null && CameraHandler.thermal_width != -1 && CameraHandler.thermal_height != -1){
                double pos_w = width * scaleGestureDetector.getScaleFactor();
                double pos_h = height * scaleGestureDetector.getScaleFactor();

                touchx = (int)(scaleGestureDetector.getFocusX());
                touchy = (int)(scaleGestureDetector.getFocusY());

                if(pos_w > 0 && pos_h > 0 && left+pos_w < CameraHandler.thermal_width && top + pos_h < CameraHandler.thermal_height){
                    width = pos_w;
                    height = pos_h;
                }
            }
            return true;
        }
    }

    public void toggleCalibrationButton(){
        runOnUiThread(() -> {
            MenuItem item = menu.findItem(R.id.calibrate);
            if(item != null) {
                if (item.isVisible()) {
                    item.setVisible(false);
                    CalibrationHandler.calibrationButtonHidden = true;
                } else {
                    item.setVisible(true);
                    CalibrationHandler.calibrationButtonHidden = false;
                }
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onClickDisconnectFlirEmulator();
        return super.onSupportNavigateUp();
    }

    public void onClickDisconnectFlirEmulator() {
        disconnectCamera();
        finish();
    }

    public void switchCamera() {
        if (findViewById(R.id.msx_image).getVisibility() == View.VISIBLE) {
            photoImage.setVisibility(View.VISIBLE);
            msxImage.setVisibility(View.INVISIBLE);
        } else {
            photoImage.setVisibility(View.INVISIBLE);
            msxImage.setVisibility(View.VISIBLE);
        }
    }

    public void switchFilter() {
        switch (curr_fusion_mode) {
            case THERMAL_ONLY:
                curr_fusion_mode = FusionMode.BLENDING;
                break;
            case BLENDING:
                curr_fusion_mode = FusionMode.MSX;
                break;
            case MSX:
                curr_fusion_mode = FusionMode.THERMAL_FUSION;
                break;
            case THERMAL_FUSION:
                curr_fusion_mode = FusionMode.PICTURE_IN_PICTURE;
                break;
            case PICTURE_IN_PICTURE:
                curr_fusion_mode = FusionMode.COLOR_NIGHT_VISION;
                break;
            case COLOR_NIGHT_VISION:
                curr_fusion_mode = FusionMode.THERMAL_ONLY;
                break;
        }
    }

    public void updateTitle(){
        String title = "";

        switch (curr_fusion_mode) {
            case THERMAL_ONLY:
                title = "Thermal Only";
                break;
            case BLENDING:
                title = "Blending";
                break;
            case MSX:
                title = "MSX";
                break;
            case THERMAL_FUSION:
                title = "Thermal Fusion";
                break;
            case PICTURE_IN_PICTURE:
                title = "Picture in Picture";
                break;
            case COLOR_NIGHT_VISION:
                title = "Night Vision";
                break;
        }

        title += " | ";

        if(CameraHandler.getTemperatureUnit() == TemperatureUnit.KELVIN) {
            title += "K";
        } else if(CameraHandler.getTemperatureUnit() == TemperatureUnit.CELSIUS){
            title += "C";
        } else if(CameraHandler.getTemperatureUnit() == TemperatureUnit.FAHRENHEIT){
            title += "F";
        }

        getSupportActionBar().setTitle(title);
    }

    /**
     * Disconnect to a camera
     */
    private void disconnectCamera() {
        updateConnectionText(connectedCameraIdentity, DISCONNECTING);
        connectedCameraIdentity = null;
        Log.d(TAG, "disconnect: Called with: connectedCameraIdentity = [" + connectedCameraIdentity + "]");
        new Thread(() -> {
            cameraHandler.disconnectCamera();
            runOnUiThread(() -> updateConnectionText(null, DISCONNECTED));
        }).start();
    }

    /**
     * Connect to a Camera
     * @param identity Camera Identity to connect to
     */
    private void connectCamera(Identity identity) {
        if (connectedCameraIdentity != null) {
            disconnectCamera();
        }

        if (identity == null) {
            Log.e(TAG, "connectCamera: No camera available");
            showMessage.show("connectCamera: No camera available");
            return;
        }

        connectedCameraIdentity = identity;

        updateConnectionText(identity, CONNECTING);
        // IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener);
        } else {
            // Spawn a new thread to connect to camera
            connectDevice(identity);
        }
    }

    /**
     * Spawns a new thread to attempt connection to the given device identity
     * @param identity the identity of the FLIR camera
     */
    private void connectDevice(Identity identity) {
        new Thread(() -> {

            try {
                cameraHandler.connectCamera(identity, connectionStatusListener);
                runOnUiThread(() -> {
                    updateConnectionText(identity, CONNECTED);
                    cameraHandler.startStream(streamDataListener);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Could not connect: " + e);
                    updateConnectionText(identity, DISCONNECTED);
                });
            }
        }).start();
    }

    /**
     * Update the UI text for connection status
     * @param identity the identity of the device that is [dis]connect(ed/ing).
     * @param status the status string to update the UI text to.
     */
    private void updateConnectionText(Identity identity, String status) {
        String deviceId = identity != null ? " " + identity.deviceId : "";
        connectionStatus.setText("Connection Status:" + deviceId + " " + status);
    }

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * Note that callbacks are received on a non-ui thread so use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    public ConnectionStatusListener connectionStatusListener = errorCode -> {
        Log.d(TAG, "onDisconnected: errorCode:" + errorCode);
        runOnUiThread(() -> updateConnectionText(connectedCameraIdentity, DISCONNECTED));
    };


    public final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {
        @Override
        public void images(BitmapFrameBuffer dataHolder) {

            runOnUiThread(() -> {
                msxImage.setImageBitmap(dataHolder.msxBitmap);
                photoImage.setImageBitmap(dataHolder.dcBitmap);
            });
        }

        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap) {

            try {
                framesBuffer.put(new BitmapFrameBuffer(msxBitmap, dcBitmap));
            } catch (InterruptedException e) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG, "images(), unable to add incoming images to frames buffer, exception:" + e);
            }

            runOnUiThread(() -> {
                Log.d(TAG, "framebuffer size:" + framesBuffer.size());
                BitmapFrameBuffer poll = framesBuffer.poll();
                if (poll != null) {
                    msxImage.setImageBitmap(poll.msxBitmap);
                    photoImage.setImageBitmap(poll.dcBitmap);
                }
            });

        }
    };


    public UsbPermissionHandler.UsbPermissionListener permissionListener = new UsbPermissionHandler.UsbPermissionListener() {
        @Override
        public void permissionGranted(@NotNull Identity identity) {
            connectDevice(identity);
        }

        @Override
        public void permissionDenied(@NotNull Identity identity) {
            FlirCameraActivity.this.showMessage.show("Permission was denied for identity ");
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            FlirCameraActivity.this.showMessage.show("Error when asking for permission for FLIR ONE, error:" + errorType + " identity:" + identity);
        }
    };
}
