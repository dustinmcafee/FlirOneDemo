package com.elotouch.flirone;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;

import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.Rectangle;
import com.flir.thermalsdk.image.TemperatureUnit;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.fusion.FusionMode;
import com.flir.thermalsdk.image.measurements.MeasurementRectangle;
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Objects;

/**
 * EHandle a FLIR ONE camera or built in emulator: Discovery, connecting and start receiving images.
 * Call all Thermal SDK listeners on a non-ui thread
 *
 * You don't *have* to specify your application to listen or USB intents but it might be beneficial for you application,
 * we are enumerating the USB devices during the discovery process which eliminates the need to listen for USB intents.
 * See the Android documentation about USB Host mode for more information
 */
class CameraHandler {

    private static final String TAG = "CameraHandler";

    private StreamDataListener streamDataListener;
    private static TemperatureUnit temperatureUnit = TemperatureUnit.CELSIUS;

    public interface StreamDataListener {
        void images(BitmapFrameBuffer dataHolder);
        void images(Bitmap msxBitmap, Bitmap dcBitmap);
    }

    // Discovered FLIR cameras
    private LinkedList<Identity> cameraIndentities = new LinkedList<>();

    // A FLIR Camera
    private Camera camera;

    public interface DiscoveryStatus {
        void started();
        void stopped();
    }

    CameraHandler() {
    }

    /**
     * Start discovery of USB and Emulators
     */
    void startDiscovery(DiscoveryEventListener cameraDiscoveryListener, DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().scan(cameraDiscoveryListener, CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.started();
    }

    /**
     * Stop discovery of USB and Emulators
     */
    void stopDiscovery(DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().stop(CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.stopped();
    }

    void connectCamera(Identity identity, ConnectionStatusListener connectionStatusListener) throws IOException {
        camera = new Camera();
        camera.connect(identity, connectionStatusListener);
    }

    void disconnectCamera() {
        if (camera != null) {
            if (camera.isGrabbing()) {
                camera.unsubscribeAllStreams();
            }
            camera.disconnect();
        }
    }

    /**
     * Start a stream of ThermalImages from the Camera (or emulator)
     *
     * @param listener CameraHandler.StreamDataListener that adds the frames to the buffer
     */
    void startStream(StreamDataListener listener) {
        this.streamDataListener = listener;
        camera.subscribeStream(thermalImageStreamListener);
    }

    /**
     * Stop a stream of ThermalImages from the Camera (or emulator)
     *
     * @param listener CameraHandler.StreamDataListener to unsubscribe
     */
    public void stopStream(ThermalImageStreamListener listener) {
        camera.unsubscribeStream(listener);
    }

    /**
     * Add a found camera to the list of known cameras
     *
     * @param identity Camera Identity to add to list of found cameras
     */
    void addFoundCameraIdentity(Identity identity) {
        cameraIndentities.add(identity);
    }

    /**
     * A getter for the cameraIdentities Linked List
     * @param i the ith element to get
     * @return the ith element of the Linked List 'cameraIdentities'
     */
    @Nullable
    public Identity getFoundCameraIdentity(int i) {
        return cameraIndentities.get(i);
    }

    /**
     * Clear all known network cameras
     */
    public void clearFoundCameraIdentities() {
        cameraIndentities.clear();
    }

    /**
     * get the C++ FLIR ONE Emulation if already found
     * @return the C++ FLIR ONE Emulation if already found, else null
     */
    @Nullable Identity getCppEmulator() {
        for (Identity foundCameraIdentity : cameraIndentities) {
            if (foundCameraIdentity.deviceId.contains("C++ Emulator")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    /**
     * get the FLIR ONE Emulation if already found
     * @return the FLIR ONE Emulation if already found, else null
     */
    @Nullable Identity getFlirOneEmulator() {
        for (Identity foundCameraIdentity : cameraIndentities) {
            if (foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    /**
     * get the FLIR ONE Camera that is not an emulation if already found
     * @return the FLIR ONE Camera if already found, else null
     */
    @Nullable Identity getFlirOne() {
        for (Identity foundCameraIdentity : cameraIndentities) {
            boolean isFlirOneEmulator = foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE");
            boolean isCppEmulator = foundCameraIdentity.deviceId.contains("C++ Emulator");
            if (!isFlirOneEmulator && !isCppEmulator) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    /**
     * Called whenever there is a new Thermal Image available, should be used in conjunction with {@link Camera.Consumer}
     */
    private final ThermalImageStreamListener thermalImageStreamListener = new ThermalImageStreamListener() {
        @Override
        public void onImageReceived() {
            //Will be called on a non-ui thread
            Log.d(TAG, "onImageReceived(), we got another ThermalImage");
            camera.withImage(receiveCameraImage);
        }
    };

    static void setTemperatureUnit(TemperatureUnit unit){
        temperatureUnit = unit;
    }

    static TemperatureUnit getTemperatureUnit(){
        return temperatureUnit;
    }

    /**
     * Function to process a Thermal Image and update UI
     */
    private final Camera.Consumer<ThermalImage> receiveCameraImage = new Camera.Consumer<ThermalImage>() {
        final double widthRatio = 1.2;
        final double heightRatio = 1.3;

        /**
         * Create the Rectangle to draw to the canvas. Will need to be translated based on Resolution ratio between different camera filters.
         * @param thermalImage the ThermalImage dataset to use as a base canvas.
         * @return the Rectangle to draw to the canvas
         */
        private Rect drawRectangle(ThermalImage thermalImage){
            Bitmap msxBitmap = BitmapAndroid.createBitmap(thermalImage.getImage()).getBitMap();

            // Set up Canvas
            Canvas canvas = new Canvas(msxBitmap);
            Rect clipBounds = canvas.getClipBounds();

            return new Rect((int) (clipBounds.right - (clipBounds.width() / widthRatio)),
                    (int) (clipBounds.bottom - (clipBounds.height() / heightRatio)),
                    (int) (clipBounds.left + (clipBounds.width() / widthRatio)),
                    (int) (clipBounds.top + (clipBounds.height() / heightRatio)));
        }

        @Override
        public void accept(ThermalImage thermalImage) {
            Log.d(TAG, "accept() called with: thermalImage = [" + thermalImage.getDescription() + "]");

            // Will be called on a non-ui thread,
            // extract information on the background thread and send the specific information to the UI thread

            //Get a bitmap with only IR data
            if(thermalImage.getFusion() != null) {
                thermalImage.getFusion().setFusionMode(FlirCameraActivity.curr_fusion_mode);
            }
            Bitmap msxBitmap = BitmapAndroid.createBitmap(thermalImage.getImage()).getBitMap();

            // Set up Canvas
            Canvas canvas = new Canvas(msxBitmap);

            // Set Temperature Unit
            thermalImage.setTemperatureUnit(temperatureUnit);

            // Get the rectangles
            Rect rect = drawRectangle(thermalImage);
            Rectangle rectangle = new Rectangle(rect.left, rect.top, rect.width(), rect.height());

            // Set the resolution multipliers
            double xMultiplier = 1;
            double yMultiplier = 1;
            if(!thermalImage.getFusion().getCurrentFusionMode().equals(FusionMode.THERMAL_ONLY)){
                xMultiplier = (double) msxBitmap.getWidth() / thermalImage.getWidth();
                yMultiplier = (double) msxBitmap.getHeight() / thermalImage.getHeight();
            }

            // Draw Rectangle
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.GREEN);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(rect, paint);

            try {
                // Scale Rectangle based on Resolution difference between THERMAL_ONLY and others
                int height = (int) (rectangle.height / yMultiplier);
                int width = (int) (rectangle.width / xMultiplier);
                int x = (int) (rectangle.x / xMultiplier);
                int y = (int) (rectangle.y / yMultiplier);
                rectangle = new Rectangle(x, y, width, height);
                int radius = (int) (5 * Math.min(xMultiplier, yMultiplier));

                thermalImage.getMeasurements().clear();
                thermalImage.getMeasurements().addRectangle(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
                MeasurementRectangle mRect = thermalImage.getMeasurements().getRectangles().get(0);
                mRect.setColdSpotMarkerVisible(true);
                mRect.setHotSpotMarkerVisible(true);
                Rectangle asdf = mRect.getRectangle();
                Rect asdfasdf = new Rect(asdf.x, asdf.y, asdf.width, asdf.height);
                paint.setColor(Color.CYAN);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(asdfasdf, paint);

                Log.e("ASDFASDFASDF", thermalImage.getMeasurements().getRectangles().get(0).toString());

                paint.setColor(Color.RED);
                canvas.drawCircle((int)(mRect.getHotSpot().x*xMultiplier), (int)(mRect.getHotSpot().y*yMultiplier), radius, paint);
                canvas.drawText("Max: " + (Math.round(mRect.getMax().value * 100.0) / 100.0) + " " + thermalImage.getStatistics().max.unit, (int)(mRect.getHotSpot().x*xMultiplier), (int)(mRect.getHotSpot().y*yMultiplier) + 15, paint);
                paint.setColor(Color.BLUE);
                canvas.drawCircle((int)(mRect.getColdSpot().x*xMultiplier), (int)(mRect.getColdSpot().y*yMultiplier), radius, paint);
                canvas.drawText("Min: " + (Math.round(mRect.getMin().value * 100.0) / 100.0) + " " + thermalImage.getStatistics().min.unit, (int)(mRect.getColdSpot().x*xMultiplier), (int)(mRect.getColdSpot().y*yMultiplier) + 15, paint);
            } catch (Exception e){
                e.printStackTrace();
                Log.e(TAG, "handleIncomingMessage: Can not draw rectangle to screen");
            }

            //Get a bitmap with the visual image, it might have different dimensions then the bitmap from THERMAL_ONLY
            Bitmap dcBitmap = BitmapAndroid.createBitmap(Objects.requireNonNull(thermalImage.getFusion().getPhoto())).getBitMap();

            Log.d(TAG,"adding images to cache");
            streamDataListener.images(msxBitmap,dcBitmap);
        }
    };
}
