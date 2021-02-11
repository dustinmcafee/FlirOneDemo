package com.elotouch.flirone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.Rectangle;
import com.flir.thermalsdk.image.TemperatureUnit;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.measurements.MeasurementException;
import com.flir.thermalsdk.image.measurements.MeasurementRectangle;
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

/**
 * EHandle a FLIR ONE camera or built in emulator: Discovery, connecting and start receiving images.
 * Call all Thermal SDK listeners on a non-ui thread
 * <p>
 * You don't *have* to specify your application to listen or USB intents but it might be beneficial for you application,
 * we are enumerating the USB devices during the discovery process which eliminates the need to listen for USB intents.
 * See the Android documentation about USB Host mode for more information
 */
class CameraHandler {

    private static final String TAG = "CameraHandler";

    private StreamDataListener streamDataListener;
    private static TemperatureUnit temperatureUnit = TemperatureUnit.CELSIUS;
    public static HashMap<Long,String> tempLog = new HashMap<>();
    Long currentReadingStartMillis;

    public interface StreamDataListener {
        void images(BitmapFrameBuffer dataHolder);

        void images(Bitmap msxBitmap, Bitmap dcBitmap);
    }

    // Discovered FLIR cameras
    private LinkedList<Identity> cameraIndentities = new LinkedList<>();

    // A FLIR Camera
    private Camera camera;
    private static FileHandler fileHandler;

    public interface DiscoveryStatus {
        void started();

        void stopped();
    }

    CameraHandler(Context context) {
        fileHandler = new FileHandler(context);
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
     *
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
     *
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
     *
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
     *
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

    static void setTemperatureUnit(TemperatureUnit unit) {
        temperatureUnit = unit;
    }

    static TemperatureUnit getTemperatureUnit() {
        return temperatureUnit;
    }

    public static double thermal_width = -1;
    public static double thermal_height = -1;

    /**
     * Function to process a Thermal Image and update UI
     */
    private final Camera.Consumer<ThermalImage> receiveCameraImage = new Camera.Consumer<ThermalImage>() {

        @Override
        public void accept(ThermalImage thermalImage) {
            Log.d(TAG, "accept() called with: thermalImage = [" + thermalImage.getDescription() + "]");
            CalibrationHandler.calibrate(thermalImage);

            thermal_width = thermalImage.getWidth();
            thermal_height = thermalImage.getHeight();

            // Will be called on a non-ui thread,
            // extract information on the background thread and send the specific information to the UI thread

            //Get a bitmap with only IR data
            if (thermalImage.getFusion() != null) {
                thermalImage.getFusion().setFusionMode(FlirCameraActivity.curr_fusion_mode);
            }
            Bitmap msxBitmap = BitmapAndroid.createBitmap(thermalImage.getImage()).getBitMap();
            //Get a bitmap with the visual image, it might have different dimensions then the bitmap from THERMAL_ONLY
            Bitmap dcBitmap = BitmapAndroid.createBitmap(Objects.requireNonNull(thermalImage.getFusion().getPhoto())).getBitMap();

            // Set Temperature Unit
            thermalImage.setTemperatureUnit(temperatureUnit);

            // calculate ratios for width and height based off the output image (which can be higher resolution) compared to the thermal image (which is lower resolutioN)
            float ratiow = (float) msxBitmap.getWidth() / thermalImage.getWidth();
            float ratioh = (float) msxBitmap.getHeight() / thermalImage.getHeight() ;

            // define a width and a height for the rectangle we are about to draw based on the ThermalImage sizes
            int width = (int)FlirCameraActivity.width;
            int height = (int)FlirCameraActivity.height;
            if (width > 0 && height > 0) {
                try {
                    // calculate left and top positioning coordinates to display the rectangle in the middle
                    float left = (float)FlirCameraActivity.left;
                    float top = (float)FlirCameraActivity.top;
                    // Create a rectangle based off those measurements in order to poll the data for statistics
                    Rectangle rect = new Rectangle((int) left, (int) top, width, height);
                    if (left + width > thermalImage.getWidth() || top + height > thermalImage.getHeight()) {
                        throw new IndexOutOfBoundsException();
                    }
                    // Set up Canvas
                    Canvas canvas = new Canvas(msxBitmap);
                    // Draw Rectangle to the high resolution image
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(Color.GREEN);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2 * ratiow);
                    canvas.drawRect(left * ratiow, top * ratioh, (left+rect.width)*ratiow, (top+rect.height)*ratioh, paint);

                    // Get statistic points and calculate them.
                    thermalImage.getMeasurements().clear();
                    thermalImage.getMeasurements().addRectangle(rect.x, rect.y, rect.width, rect.height);
                    MeasurementRectangle mRect = thermalImage.getMeasurements().getRectangles().get(0);
                    mRect.setColdSpotMarkerVisible(true);
                    mRect.setHotSpotMarkerVisible(true);

                    double min = (Math.round(mRect.getMin().value * 100.0) / 100.0);
                    double max = (Math.round(mRect.getMax().value * 100.0) / 100.0);
                    double avg = (Math.round((mRect.getAverage().value) * 100.0) / 100.0);

                    long curr_time = System.currentTimeMillis();
                    if(tempLog == null || tempLog.size() == 0){
//                        Log.e("ANDREI", "reset time");
                        currentReadingStartMillis = curr_time;
                    }

                    // TODO - change this to 5 min instead of 15 sec
                        if((curr_time- currentReadingStartMillis )/1000 > 15){
                            Log.e("ANDREI", "Saving current log and clearning log queue");
                            saveLog(FlirCameraActivity.getInstance(),true);
                            tempLog.clear();
                        } else{
                            tempLog.put(curr_time, "Min: " + min + "; Max: " + max + "; Avg: " + avg);
                        }



                    paint.setTextSize(20 * ratiow);
                    paint.setStyle(Paint.Style.FILL);
                    // Draw avg temp text
                    canvas.drawText("Avg: " + avg + " " + thermalImage.getTemperatureUnit().toString().charAt(0), left * ratiow,(top -5)*ratioh,paint);

                    // Draw min/max temperature points
                    paint.setColor(Color.RED);
                    canvas.drawCircle((int)(mRect.getHotSpot().x*ratiow), (int)(mRect.getHotSpot().y*ratioh), 5 * ratiow, paint);
                    canvas.drawText(max + " " + thermalImage.getTemperatureUnit().toString().charAt(0), mRect.getHotSpot().x * ratiow, (mRect.getHotSpot().y + 20)*ratioh, paint);
                    paint.setColor(Color.BLUE);
                    canvas.drawCircle(mRect.getColdSpot().x*ratiow, mRect.getColdSpot().y*ratioh, 5 * ratiow, paint);
                    canvas.drawText(min+ " " + thermalImage.getTemperatureUnit().toString().charAt(0), mRect.getColdSpot().x * ratiow, (mRect.getColdSpot().y + 20)*ratioh, paint);

                } catch (IndexOutOfBoundsException | MeasurementException e){
                    e.printStackTrace();
                }
            }

            Log.d(TAG, "adding images to cache");
//            fileHandler.saveImage(thermalImage, String.valueOf(System.currentTimeMillis()) + ".jpeg");
            streamDataListener.images(msxBitmap, dcBitmap);
        }
    };


    public static void saveLog(Context ctx,boolean shouldAppend) {
        StringBuilder msgLog = new StringBuilder();
        Date date;

        if (CameraHandler.tempLog.size() != 0) {
            for (Map.Entry<Long, String> entry : CameraHandler.tempLog.entrySet()) {
                date = new Date(entry.getKey());
                msgLog.append(date.toString()).append(": \t ").append(entry.getValue()).append("\n");
            }
        } else {
            msgLog.append("There are no logs recorded.");
        }

        FileWriter out = null;
        try {
            Date d = new Date(System.currentTimeMillis());
            String filename = d.toString();
            if(shouldAppend){
                DateFormat formatter = new SimpleDateFormat("MM-dd-yyyy");
                filename = formatter.format(d);
                filename+="-FULL";
            } else{
                DateFormat formatter = new SimpleDateFormat("MM-dd-yyyy-HH:mm:ss");
                filename = formatter.format(d);
                filename+="-SHORT";
            }
            String path = ctx.getExternalFilesDir("logs").getAbsolutePath();
            out = new FileWriter(new File(path, filename),shouldAppend);
            out.write(msgLog.toString());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void resetLog() {
        CameraHandler.tempLog.clear();
    }

}
