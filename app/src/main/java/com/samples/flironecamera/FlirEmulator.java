package com.samples.flironecamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
import java.util.concurrent.LinkedBlockingQueue;

import static com.samples.flironecamera.FlirCameraApplication.cameraHandler;
import static com.samples.flironecamera.FlirCameraApplication.connectedIdentity;

public class FlirEmulator extends AppCompatActivity {
    private static final String TAG = "FlirEmulator";

    public MainActivity.ShowMessage showMessage = message -> Toast.makeText(FlirEmulator.this, message, Toast.LENGTH_SHORT).show();

    public UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    public LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue<>(21);

    public static FusionMode curr_fusion_mode = FusionMode.THERMAL_ONLY;

    private TextView connectionStatus;

    private CustomImageView msxImage;
    private ImageView photoImage;
    private static Rect rectangle = new Rect(0, 0, 0, 0);
    private static Rect baseRectangle = new Rect(0, 0, 0, 0);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.DarkTheme);
        setContentView(R.layout.flir_emulator_main);
        msxImage = findViewById(R.id.msx_image);
        photoImage = findViewById(R.id.photo_image);
        connectionStatus = findViewById(R.id.connection_status_text);

        // Show Thermal Android SDK version
        TextView sdkVersionTextView = findViewById(R.id.sdk_version);
        String sdkVersionText = getString(R.string.sdk_version_text, ThermalSdkAndroid.getVersion());
        sdkVersionTextView.setText(sdkVersionText);

        // TODO: Set default behavior if getIntent == null: Log error. (not that it ever should, but it will fix the lint error)
        switch (getIntent().getAction()) {
            case MainActivity.ACTION_START_FLIR_ONE:
                connect(cameraHandler.getFlirOne());
                break;
            case MainActivity.ACTION_START_SIMULATOR_ONE:
                connect(cameraHandler.getCppEmulator());
                break;
            case MainActivity.ACTION_START_SIMULATOR_TWO:
                connect(cameraHandler.getFlirOneEmulator());
                break;
        }
    }

    private static Rect setRectangle(int left, int top, int right, int bottom){
        rectangle.set(left, top, right, bottom);
        cameraHandler.setRectangle(rectangle);
        return rectangle;
    }

    private static Rect setBaseRectangle(int left, int top, int right, int bottom){
        baseRectangle.set(left, top, right, bottom);
        cameraHandler.setBaseRectangle(baseRectangle);
        return baseRectangle;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar1, menu);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_toolbar_back);

        return super.onCreateOptionsMenu(menu);
    }

    public static class CustomImageView extends androidx.appcompat.widget.AppCompatImageView {
        private Paint paint;

        public CustomImageView(Context context) {
            super(context);
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.MAGENTA);
            paint.setStyle(Paint.Style.STROKE);
        }

        public CustomImageView(Context context, AttributeSet attrs, Paint paint) {
            super(context, attrs);
            this.paint = paint;
        }

        public CustomImageView(Context context, AttributeSet attrs) {
            super(context, attrs);
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.MAGENTA);
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
//            Log.e("ASDFASDFASDF - draw", "left: " + String.valueOf(getLeft()+(getRight()-getLeft())/3) + " top: " + String.valueOf(getBottom()-(getBottom()-getTop())/1.2) + " right: " + String.valueOf(getRight()-(getRight()-getLeft())/3) + " bottom: " + String.valueOf(getTop()+(getBottom()-getTop())/1.2));
//            Log.e("ASDFASDFASDF - base", "left: " + String.valueOf(getLeft()) + " top: " + String.valueOf(getTop()) + " right: " + String.valueOf(getRight()) + " bottom: " + String.valueOf(getBottom()));
//            Log.e("ASDFASDFASDF-otherbase", "left: " + String.valueOf(getRight()-(getRight()-getLeft())/1) + " top: " + String.valueOf(getBottom()-(getBottom()-getTop())/1) + " right: " + String.valueOf(getLeft()+(getRight()-getLeft())/1) + " bottom: " + String.valueOf(getTop()+(getBottom()-getTop())/1));
            Rect rectangle = setRectangle(getLeft()+(getRight()-getLeft())/3,
                    (int) (getBottom()-(getBottom()-getTop())/1.2),
                    getRight()-(getRight()-getLeft())/3,
                    (int) (getTop()+(getBottom()-getTop())/1.2));
            setBaseRectangle(getLeft(),getTop(),getRight(),getBottom());
            canvas.drawRect(rectangle, paint);
        }
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
            case R.id.toolbar_temperature:
                if(CameraHandler.getTemperatureUnit() == TemperatureUnit.KELVIN) {
                    CameraHandler.setTemperatureUnit(TemperatureUnit.CELSIUS);
                } else if(CameraHandler.getTemperatureUnit() == TemperatureUnit.CELSIUS){
                    CameraHandler.setTemperatureUnit(TemperatureUnit.FAHRENHEIT);
                } else if(CameraHandler.getTemperatureUnit() == TemperatureUnit.FAHRENHEIT){
                    CameraHandler.setTemperatureUnit(TemperatureUnit.KELVIN);
                }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onClickDisconnectFlirEmulator();
        return super.onSupportNavigateUp();
    }

    public void onClickDisconnectFlirEmulator() {
        disconnect();
        finish();
    }

    public void switchCamera() {
        if (findViewById(R.id.msx_image).getVisibility() == View.VISIBLE) {
            findViewById(R.id.msx_image).setVisibility(View.INVISIBLE);
            findViewById(R.id.photo_image).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.msx_image).setVisibility(View.VISIBLE);
            findViewById(R.id.photo_image).setVisibility(View.INVISIBLE);
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
     * Connect to a Camera
     */
    private void connect(Identity identity) {
        if (connectedIdentity != null) {
            // Should we support more than one camera at a time? what would be the use case?
            Log.d(TAG, "connect:, Support only one camera connection at the time");
            showMessage.show("connect:, Support only one camera connection at the time");
            return;
        }

        if (identity == null) {
            Log.d(TAG, "connect:, No camera available");
            showMessage.show("connect:, No camera available");
            return;
        }

        connectedIdentity = identity;

        updateConnectionText(identity, "CONNECTING");
        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener);
        } else {
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
                cameraHandler.connect(identity, connectionStatusListener);
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
     */
    private void updateConnectionText(Identity identity, String status) {
        String deviceId = identity != null ? " " + identity.deviceId : "";
        connectionStatus.setText("Connection Status:" + deviceId + " " + status);
    }

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    public ConnectionStatusListener connectionStatusListener = errorCode -> {
        Log.d(TAG, "onDisconnected errorCode:" + errorCode);

        runOnUiThread(() -> updateConnectionText(connectedIdentity, "DISCONNECTED"));
    };

    public final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {

        @Override
        public void images(FrameDataHolder dataHolder) {

            runOnUiThread(() -> {
                msxImage.setImageBitmap(dataHolder.msxBitmap);
                photoImage.setImageBitmap(dataHolder.dcBitmap);
            });
        }

        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap) {

            try {
                framesBuffer.put(new FrameDataHolder(msxBitmap, dcBitmap));
            } catch (InterruptedException e) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG, "images(), unable to add incoming images to frames buffer, exception:" + e);
            }

            runOnUiThread(() -> {
                Log.d(TAG, "framebuffer size:" + framesBuffer.size());
                FrameDataHolder poll = (FrameDataHolder) framesBuffer.poll();
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
            FlirEmulator.this.showMessage.show("Permission was denied for identity ");
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            FlirEmulator.this.showMessage.show("Error when asking for permission for FLIR ONE, error:" + errorType + " identity:" + identity);
        }
    };
}
