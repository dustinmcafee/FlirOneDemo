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
     * Represents simple statistics of a given dataset, and their respective (X,Y) Coordinates
     */
    private static class StatisticPoint {
        double min;
        double max;
        double average;
        Point hotSpot;
        Point coldSpot;
        StatisticPoint(int minX, int minY, int maxX, int maxY, double min, double max, double average){
            this.hotSpot = new Point(maxX, maxY);
            this.coldSpot = new Point(minX, minY);
            this.min = min;
            this.max = max;
            this.average = average;
        }
        StatisticPoint(Point hotSpot, Point coldSpot, double min, double max, double average){
            this.hotSpot = hotSpot;
            this.coldSpot = coldSpot;
            this.min = min;
            this.max = max;
            this.average = average;
        }
    }

    /**
     * Return the {@link StatisticPoint} of a given set of values inside a given rectangle.
     *      The rectangle is used to translate the (X,Y) Coordinates of the StatisticPoint
     * @param vals the dataset
     * @param width the width of the rectangle
     * @param left the left of the rectangle
     * @param top the top of the rectangle
     * @return the StatisticPoint of the given input
     */
    private StatisticPoint getStats(double[] vals, int width, int left, int top){
        if(vals.length == 0){
            return null;
        }
//        int height = vals.length/width;
        double min = Double.MAX_VALUE;
        int minI = -1;
        int maxI = -1;
        double max = Double.MIN_VALUE;
        double average = 0;
        int i = 0;
        for (double val : vals){
            if (val < min){
                min = val;
                minI = i;
            }
            if (val > max){
                max = val;
                maxI = i;
            }
            average += val;
            i++;
        }
        average /= vals.length;
        int minX = (minI%width) + left;
        int minY = (minI/width) + top;
        int maxX = (maxI%width) + left;
        int maxY = (maxI/width) + top;
        return new StatisticPoint(minX, minY, maxX, maxY, min, max, average);
    }

    /**
     * Function to process a Thermal Image and update UI
     */
    private final Camera.Consumer<ThermalImage> receiveCameraImage = new Camera.Consumer<ThermalImage>() {
        final double widthRatio = 1.2;
        final double heightRatio = 1.3;
        final double multiplier = 2.25; // TODO: Make this work dynamically

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

            // Set Temperature Unit
            thermalImage.setTemperatureUnit(temperatureUnit);

            // Get the rectangles
            Rect rect = drawRectangle(thermalImage);
            Rectangle rectangle = new Rectangle(rect.left, rect.top, rect.width(), rect.height());

            // Set up Canvas
            Canvas canvas = new Canvas(msxBitmap);

            // Draw Rectangle
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.GREEN);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(rect, paint);

            try {
                // Scale Rectangle based on Resolution difference between THERMAL_ONLY and others
                // TODO: Make this dynamic: 'multiplier' should be based on current filter mode.
                if(!thermalImage.getFusion().getCurrentFusionMode().equals(FusionMode.THERMAL_ONLY)){
                    int height = (int) (rectangle.height / multiplier);
                    int width = (int) (rectangle.width / multiplier);
                    int x = (int) (rectangle.x / multiplier);
                    int y = (int) (rectangle.y / multiplier);
                    rectangle = new Rectangle(x, y, width, height);
                }

                // Compute Statistics
                double[] vals = thermalImage.getValues(rectangle);
                StatisticPoint rectStats = getStats(vals, rect.width(), rect.left, rect.top);

                if (rectStats != null) {
                    // Draw min/max temperature points
                    paint.setColor(Color.RED);
                    canvas.drawCircle(rectStats.hotSpot.x, rectStats.hotSpot.y, 5, paint);
                    canvas.drawText("Max: " + (Math.round(rectStats.max * 100.0) / 100.0) + " " + thermalImage.getStatistics().max.unit, rectStats.hotSpot.x, rectStats.hotSpot.y + 15, paint);
                    paint.setColor(Color.BLUE);
                    canvas.drawCircle(rectStats.coldSpot.x, rectStats.coldSpot.y, 5, paint);
                    canvas.drawText("Min: " + (Math.round(rectStats.min * 100.0) / 100.0) + " " + thermalImage.getStatistics().min.unit, rectStats.coldSpot.x, rectStats.coldSpot.y + 15, paint);
                }
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
