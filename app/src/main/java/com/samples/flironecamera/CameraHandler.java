/*******************************************************************
 * @title FLIR THERMAL SDK
 * @file CameraHandler.java
 * @Author FLIR Systems AB
 *
 * @brief Helper class that encapsulates *most* interactions with a FLIR ONE camera
 *
 * Copyright 2019:    FLIR Systems
 ********************************************************************/
package com.samples.flironecamera;

import android.app.Application;
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
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Encapsulates the handling of a FLIR ONE camera or built in emulator, discovery, connecting and start receiving images.
 * All listeners are called from Thermal SDK on a non-ui thread
 * <p/>
 * Usage:
 * <pre>
 * Start discovery of FLIR FLIR ONE cameras or built in FLIR ONE cameras emulators
 * {@linkplain #startDiscovery(DiscoveryEventListener, DiscoveryStatus)}
 * Use a discovered Camera {@linkplain Identity} and connect to the Camera
 * (note that calling connect is blocking and it is mandatory to call this function from a background thread):
 * {@linkplain #connect(Identity, ConnectionStatusListener)}
 * Once connected to a camera
 * {@linkplain #startStream(StreamDataListener)}
 * </pre>
 * <p/>
 * You don't *have* to specify your application to listen or USB intents but it might be beneficial for you application,
 * we are enumerating the USB devices during the discovery process which eliminates the need to listen for USB intents.
 * See the Android documentation about USB Host mode for more information
 * <p/>
 * Please note, this is <b>NOT</b> production quality code, error handling has been kept to a minimum to keep the code as clear and concise as possible
 */
class CameraHandler {

    private static final String TAG = "CameraHandler";

    private StreamDataListener streamDataListener;
    private static TemperatureUnit temperatureUnit = TemperatureUnit.CELSIUS;
    private Rectangle rectangle;
    private Rect baseRectangle;
    private int[] rectangleCoords;

    public interface StreamDataListener {
        void images(FrameDataHolder dataHolder);
        void images(Bitmap msxBitmap, Bitmap dcBitmap);
    }


    //Discovered FLIR cameras
    LinkedList<Identity> foundCameraIdentities = new LinkedList<>();

    //A FLIR Camera
    private Camera camera;


    public interface DiscoveryStatus {
        void started();
        void stopped();
    }

    public CameraHandler() {
    }

    /**
     * Start discovery of USB and Emulators
     */
    public void startDiscovery(DiscoveryEventListener cameraDiscoveryListener, DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().scan(cameraDiscoveryListener, CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.started();
    }

    /**
     * Stop discovery of USB and Emulators
     */
    public void stopDiscovery(DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().stop(CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.stopped();
    }

    public void connect(Identity identity, ConnectionStatusListener connectionStatusListener) throws IOException {
        camera = new Camera();
        camera.connect(identity, connectionStatusListener);
    }

    public void disconnect() {
        if (camera == null) {
            return;
        }
        if (camera.isGrabbing()) {
            camera.unsubscribeAllStreams();
        }
        camera.disconnect();
    }


    public void setRectangle(Rect rectangle){
        this.rectangle = new Rectangle(rectangle.left, rectangle.top, Math.abs(rectangle.width()), Math.abs(rectangle.height()));
        this.rectangleCoords = new int[] {rectangle.left, rectangle.top, rectangle.right, rectangle.bottom};
    }

    public void setBaseRectangle(Rect rectangle){
        this.baseRectangle = rectangle;
    }

    /**
     * Start a stream of {@link ThermalImage}s images from a FLIR ONE or emulator
     */
    public void startStream(StreamDataListener listener) {
        this.streamDataListener = listener;
        camera.subscribeStream(thermalImageStreamListener);
    }

    /**
     * Stop a stream of {@link ThermalImage}s images from a FLIR ONE or emulator
     */
    public void stopStream(ThermalImageStreamListener listener) {
        camera.unsubscribeStream(listener);
    }

    /**
     * Add a found camera to the list of known cameras
     */
    public void add(Identity identity) {
        foundCameraIdentities.add(identity);
    }

    @Nullable
    public Identity get(int i) {
        return foundCameraIdentities.get(i);
    }

    /**
     * Get a read only list of all found cameras
     */
    @Nullable
    public List<Identity> getCameraList() {
        return Collections.unmodifiableList(foundCameraIdentities);
    }

    /**
     * Clear all known network cameras
     */
    public void clear() {
        foundCameraIdentities.clear();
    }

    @Nullable
    public Identity getCppEmulator() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("C++ Emulator")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    @Nullable
    public Identity getFlirOneEmulator() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    @Nullable
    public Identity getFlirOne() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
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
            camera.withImage(handleIncomingImage);
        }
    };

    public static void setTemperatureUnit(TemperatureUnit unit){
        temperatureUnit = unit;
    }

    public static TemperatureUnit getTemperatureUnit(){
        return temperatureUnit;
    }

    private class StatisticPoint {
        int minX;
        int minY;
        int maxX;
        int maxY;
        double min;
        double max;
        double average;
        StatisticPoint(int minX, int minY, int maxX, int maxY, double min, double max, double average){
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.min = min;
            this.max = max;
            this.average = average;
        }
    }

    private StatisticPoint getStats(double[] vals, int width){
        if(vals.length == 0){
            return null;
        }
        int height = vals.length/width;
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
        int minX = minI%width;
        int minY = minI/width;
        int maxX = maxI%width;
        int maxY = maxI/width;
        return new StatisticPoint(minX, minY, maxX, maxY, min, max, average);
    }

    /**
     * Function to process a Thermal Image and update UI
     */
    private final Camera.Consumer<ThermalImage> handleIncomingImage = new Camera.Consumer<ThermalImage>() {
        @Override
        public void accept(ThermalImage thermalImage) {
            Log.d(TAG, "accept() called with: thermalImage = [" + thermalImage.getDescription() + "]");
            // Will be called on a non-ui thread,
            // extract information on the background thread and send the specific information to the UI thread

            //Get a bitmap with only IR data
            Bitmap msxBitmap;
            {
                thermalImage.getFusion().setFusionMode(FlirEmulator.curr_fusion_mode);
                msxBitmap = BitmapAndroid.createBitmap(thermalImage.getImage()).getBitMap();
            }

            // Set Temperature Unit
            thermalImage.setTemperatureUnit(temperatureUnit);

            // Set up Canvas
            Canvas canvas = new Canvas(msxBitmap);
            Rect clipBounds = canvas.getClipBounds();
            int scaleX = (baseRectangle.right - baseRectangle.left)/(clipBounds.right - clipBounds.left);
            int scaleY = (baseRectangle.bottom - baseRectangle.top)/(clipBounds.bottom - clipBounds.top);

            Log.e("ASDFASDFASDF-clipBounds", "left: " + clipBounds.left + " top: " + clipBounds.top + " right: " + clipBounds.right + " bottom: " + clipBounds.bottom);

            // Set Rectangle, get statistics
            int width = rectangle.width / scaleX;
            int height = rectangle.height / scaleY;
            int left = rectangleCoords[0] / scaleX;
//            int left = rectangleCoords[0];
            int top = rectangleCoords[1] / scaleY;
            int right = rectangleCoords[2] / scaleX;
            int bottom = rectangleCoords[3] / scaleY;
            Log.e("ASDFASDFASDF-rect", "left: " + left + " top: " + top + " right: " + right + " bottom: " + bottom + " width: " + width + " height: " + height);
            Rectangle rect = new Rectangle(left, top, width, height);
            Rect rect1 = new Rect(left, top, right, bottom);
            double[] vals = thermalImage.getValues(rect);
            StatisticPoint rectStats = getStats(vals, width);

            if(rectStats != null) {
                Log.e("ASDF Dustin", rectStats.toString());
                Point hotSpot = new Point();
                hotSpot.x = rectStats.maxX;
                hotSpot.y = rectStats.maxY;
                double max = rectStats.max;
                Point coldSpot = new Point();
                coldSpot.x = rectStats.minX;
                coldSpot.y = rectStats.minY;
                double min = rectStats.min;


                // Draw min/max temperature points
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.RED);
//            canvas.drawCircle(thermalImage.getStatistics().hotSpot.x,thermalImage.getStatistics().hotSpot.y,5,paint);
//            canvas.drawText("Max: " + (Math.round(thermalImage.getStatistics().max.value * 100.0) / 100.0) + " " + thermalImage.getStatistics().max.unit,thermalImage.getStatistics().hotSpot.x,thermalImage.getStatistics().hotSpot.y + 15,paint);
                canvas.drawCircle(hotSpot.x, hotSpot.y, 5, paint);
                canvas.drawText("Max: " + (Math.round(max * 100.0) / 100.0) + " " + thermalImage.getStatistics().max.unit, hotSpot.x, hotSpot.y + 15, paint);
                paint.setColor(Color.BLUE);
//            canvas.drawCircle(thermalImage.getStatistics().coldSpot.x,thermalImage.getStatistics().coldSpot.y,5,paint);
//            canvas.drawText("Min: " + (Math.round(thermalImage.getStatistics().min.value * 100.0) / 100.0)  + " " + thermalImage.getStatistics().min.unit,thermalImage.getStatistics().coldSpot.x,thermalImage.getStatistics().coldSpot.y + 15,paint);
                canvas.drawCircle(coldSpot.x, coldSpot.y, 5, paint);
                canvas.drawText("Min: " + (Math.round(min * 100.0) / 100.0) + " " + thermalImage.getStatistics().min.unit, coldSpot.x, coldSpot.y + 15, paint);


                paint.setColor(Color.GREEN);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(rect1, paint);
            }

            //Get a bitmap with the visual image, it might have different dimensions then the bitmap from THERMAL_ONLY
            Bitmap dcBitmap = BitmapAndroid.createBitmap(thermalImage.getFusion().getPhoto()).getBitMap();


            Log.d(TAG,"adding images to cache");
            streamDataListener.images(msxBitmap,dcBitmap);
        }
    };
}
