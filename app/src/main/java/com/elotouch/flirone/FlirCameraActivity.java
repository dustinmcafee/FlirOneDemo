package com.elotouch.flirone;

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

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

import static com.elotouch.flirone.FlirCameraApplication.cameraHandler;
import static com.elotouch.flirone.FlirCameraApplication.connectedCameraIdentity;

public class FlirCameraActivity extends AppCompatActivity {
    private static final String TAG = "FlirCameraActivity";

    public MainActivity.ShowMessage showMessage = message -> Toast.makeText(FlirCameraActivity.this, message, Toast.LENGTH_SHORT).show();

    public UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    public LinkedBlockingQueue<BitmapFrameBuffer> framesBuffer = new LinkedBlockingQueue<>(21);

    public static FusionMode curr_fusion_mode = FusionMode.THERMAL_ONLY;

    private TextView connectionStatus;

    private ImageView msxImage;
    private ImageView photoImage;

    ScaleGestureDetector mScaleGestureDetector;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.DarkTheme);
        setContentView(R.layout.flir_emulator_main);
        msxImage = findViewById(R.id.msx_image);
        photoImage = findViewById(R.id.photo_image);
        connectionStatus = findViewById(R.id.connection_status_text);

        mScaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        // Show Thermal Android SDK version
        TextView sdkVersionTextView = findViewById(R.id.sdk_version);
        String sdkVersionText = getString(R.string.sdk_version_text, ThermalSdkAndroid.getVersion());
        sdkVersionTextView.setText(sdkVersionText);

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar1, menu);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_toolbar_back);

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
                break;
            case R.id.toolbar_reset:
                if(msxImage != null && photoImage != null){
                    width = CameraHandler.thermal_width/2.0;
                    height = width;
                    left = CameraHandler.thermal_width - width/2;
                    top = CameraHandler.thermal_height - height/2;
                }
                break;

        }
        return super.onOptionsItemSelected(item);
    }


    public static double left = 0;
    public static double top = 0;
    public static double width = 200;
    public static double height = 200;

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if(msxImage != null){
            int[] viewCoords = new int[2];
            msxImage.getLocationInWindow(viewCoords);
            int imageX = (int)(event.getX() - viewCoords[0]);
            int imageY = (int)(event.getY() - viewCoords[1]);

            float ratiow = (float) CameraHandler.thermal_width / msxImage.getWidth();
            float ratioh = (float) CameraHandler.thermal_height / msxImage.getHeight();

            Log.e("ANDREI", imageX + "  " + imageY);
            Log.e("ANDREI", ratiow + "  " + ratioh);

            if(event.getX() - (width / 2)/ratiow > viewCoords[0]){
                if(event.getX() + (width/2)/ratiow < viewCoords[0] + msxImage.getWidth()){
                    Log.e("ANDREI", "HERE 1");

                    left = imageX * ratiow - width/2;
                } else{
                    Log.e("ANDREI", "HERE 2");

                    left = CameraHandler.thermal_width - width;
                }
            } else{
                Log.e("ANDREI", "HERE 3");

                left = 0;
            }
            if(event.getY() - (height / 2)/ratioh >viewCoords[1]){
                if(event.getY() + (height/2)/ratioh < viewCoords[1] + msxImage.getHeight()){
                    Log.e("ANDREI", "HERE 4");

                    top = imageY * ratioh - height/2;
                } else{
                    Log.e("ANDREI", "HERE 5");

                    top = CameraHandler.thermal_height - height;
                }
            } else{
                Log.e("ANDREI", "HERE 6");

                top = 0;
            }
        }

        mScaleGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector){
            if(msxImage != null && photoImage!=null){
                double pos_w = width * scaleGestureDetector.getScaleFactor();
                double pos_h = height * scaleGestureDetector.getScaleFactor();

                if(pos_w > 0 && pos_h > 0 && left+pos_w < CameraHandler.thermal_width && top + pos_h < CameraHandler.thermal_height){
                    width = pos_w;
                    height = pos_h;
                }
            }
            return true;
        }
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
                Toast.makeText(getApplicationContext(),"Mode: BLENDING",Toast.LENGTH_SHORT).show();
                break;
            case BLENDING:
                curr_fusion_mode = FusionMode.MSX;
                Toast.makeText(getApplicationContext(),"Mode: MSX",Toast.LENGTH_SHORT).show();
                break;
            case MSX:
                curr_fusion_mode = FusionMode.THERMAL_FUSION;
                Toast.makeText(getApplicationContext(),"Mode: THERMAL_FUSION",Toast.LENGTH_SHORT).show();
                break;
            case THERMAL_FUSION:
                curr_fusion_mode = FusionMode.PICTURE_IN_PICTURE;
                Toast.makeText(getApplicationContext(),"Mode: PICTURE_IN_PICTURE",Toast.LENGTH_SHORT).show();
                break;
            case PICTURE_IN_PICTURE:
                curr_fusion_mode = FusionMode.COLOR_NIGHT_VISION;
                Toast.makeText(getApplicationContext(),"Mode: COLOR_NIGHT_VISION",Toast.LENGTH_SHORT).show();
                break;
            case COLOR_NIGHT_VISION:
                curr_fusion_mode = FusionMode.THERMAL_ONLY;
                Toast.makeText(getApplicationContext(),"Mode: THERMAL_ONLY",Toast.LENGTH_SHORT).show();
                break;
        }
    }

    /**
     * Disconnect to a camera
     */
    private void disconnectCamera() {
        updateConnectionText(connectedCameraIdentity, "DISCONNECTING");
        connectedCameraIdentity = null;
        Log.d(TAG, "disconnect: Called with: connectedCameraIdentity = [" + connectedCameraIdentity + "]");
        new Thread(() -> {
            cameraHandler.disconnectCamera();
            runOnUiThread(() -> updateConnectionText(null, "DISCONNECTED"));
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

        updateConnectionText(identity, "CONNECTING");
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
                    updateConnectionText(identity, "CONNECTED");
                    cameraHandler.startStream(streamDataListener);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Could not connect: " + e);
                    updateConnectionText(identity, "DISCONNECTED");
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
        runOnUiThread(() -> updateConnectionText(connectedCameraIdentity, "DISCONNECTED"));
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
