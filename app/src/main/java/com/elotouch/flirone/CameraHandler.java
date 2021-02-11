package com.elotouch.flirone;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;

import androidx.renderscript.RenderScript;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import androidx.annotation.RequiresApi;

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
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

import io.github.silvaren.easyrs.tools.Nv21Image;

import static android.content.Context.CAMERA_SERVICE;

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
    public static TemperatureUnit temperatureUnit = TemperatureUnit.CELSIUS;
    static HashMap<Long,String> tempLog = new HashMap<>();
    private Long currentReadingStartMillis;

    public interface StreamDataListener {
        void images(BitmapFrameBuffer dataHolder);

        void images(Bitmap msxBitmap, Bitmap dcBitmap);
    }

    // Discovered FLIR cameras
    private LinkedList<Identity> cameraIndentities = new LinkedList<>();

    // A FLIR Camera
    private Camera camera;

    private Context mContext;
    private Context mActivityContext;
    RenderScript rs;
    public LinkedBlockingQueue<BitmapFrameBuffer> framesBuffer = new LinkedBlockingQueue<>(21);

    public interface DiscoveryStatus {
        void started();

        void stopped();
    }

    CameraHandler(Context context, Context activityContext, RenderScript rs) {
        this.mContext = context;
        this.mActivityContext = activityContext;
        this.rs = rs;
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

    static double thermal_width = -1;
    static double thermal_height = -1;

    /**
     * Function to process a Thermal Image and update UI
     */
    private final Camera.Consumer<ThermalImage> receiveCameraImage = new Camera.Consumer<ThermalImage>() {

        @Override
        public void accept(ThermalImage newThermalImage) {
            Log.d(TAG, "accept() called with: thermalImage = [" + newThermalImage.getDescription() + "]");
            try {
                thermal_width = newThermalImage.getWidth();
                thermal_height = newThermalImage.getHeight();
                if (framesBuffer.isEmpty()) {
                    Bitmap msxBitmap = BitmapAndroid.createBitmap(newThermalImage.getImage()).getBitMap();
                    drawGuideRectangle(newThermalImage, msxBitmap);

                    // Firebase Face Detection takes time, so drawFaceRectangle will push Bitmaps and Face Rectangle into stacks.
                    Bitmap dcBitmap = null;
                    if(newThermalImage.getFusion() != null && newThermalImage.getFusion().getPhoto() != null) {
                        // Get a bitmap with the visual image, it might have different dimensions then the bitmap from THERMAL_ONLY
                        dcBitmap = BitmapAndroid.createBitmap(newThermalImage.getFusion().getPhoto()).getBitMap();
                        drawFaceRectangle(dcBitmap, msxBitmap);
                    } else {
                        framesBuffer.put(new BitmapFrameBuffer(msxBitmap, dcBitmap, null));
                    }
                    return;
                }
                BitmapFrameBuffer frame = framesBuffer.poll();
                if(frame == null){
                    Log.e(TAG, "FRAME IS NULL");
                    return;
                }
                Bitmap msxBitmap = frame.msxBitmap;
                Bitmap dcBitmap = frame.dcBitmap;
                Rect msxFaceRect = frame.faceRect;
                Log.d(TAG, "adding images to cache");
                streamDataListener.images(msxBitmap, dcBitmap);

                // Set up Canvas & Paint
                Canvas canvas = new Canvas(msxBitmap);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                // Draw Face Rectangle
                if(msxFaceRect != null) {
                    paint.setStrokeWidth(4);
                    paint.setColor(Color.MAGENTA);
                    paint.setStyle(Paint.Style.STROKE);
                    try {
                        canvas.drawRect(msxFaceRect, paint);
                    } catch (Exception e) {
                        Log.e("ASDF", "ASDFSADFSADF");
                        e.printStackTrace();
                    }
                }

            } catch (Exception e){
                Log.e(TAG, "BAD!!!!!");
                e.printStackTrace();
            }
        }
    };

    private void drawGuideRectangle(ThermalImage thermalImage, Bitmap msxBitmap){
        CalibrationHandler.calibrate(thermalImage);
        Canvas canvas = new Canvas(msxBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Get Ratios
        float ratiow = (float) msxBitmap.getWidth() / (float) thermalImage.getWidth();
        float ratioh = (float) msxBitmap.getHeight() / (float) thermalImage.getHeight();
        int width = (int)FlirCameraActivity.width;
        int height = (int)FlirCameraActivity.height;
        if (width <= 0 && height <= 0) {
            return;
        }

        // calculate left and top positioning coordinates to display the rectangle in the middle
        float left = (float)FlirCameraActivity.left;
        float top = (float)FlirCameraActivity.top;

        // Create a rectangle based off those measurements in order to poll the data for statistics
        Rectangle rect = new Rectangle((int) left, (int) top, width, height);
        if (left + width > thermalImage.getWidth() || top + height > thermalImage.getHeight()) {
            throw new IndexOutOfBoundsException();
        }

        // Draw Rectangle
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * ratiow);
        canvas.drawRect(left * ratiow, top * ratioh, (left+rect.width)*ratiow, (top+rect.height)*ratioh, paint);

        // Get statistic points and calculate them.
        MeasurementRectangle mRect;
        try {
            thermalImage.getMeasurements().clear();
            thermalImage.getMeasurements().addRectangle(rect.x, rect.y, rect.width, rect.height);
            mRect = thermalImage.getMeasurements().getRectangles().get(0);
        } catch (Exception e){
            Log.e(TAG, "Could not calculate Guide Rectangle in Thermal Image");
            e.printStackTrace();
            return;
        }
        try {
            mRect.setColdSpotMarkerVisible(true);
            mRect.setHotSpotMarkerVisible(true);
        } catch (MeasurementException e){
            e.printStackTrace();
        }
        double min = (Math.round(mRect.getMin().value * 100.0) / 100.0);
        double max = (Math.round(mRect.getMax().value * 100.0) / 100.0);
        double avg = (Math.round((mRect.getAverage().value) * 100.0) / 100.0);

        // Write to log
        // TODO: Should this also be implemented for the facial detection square?
        long curr_time = System.currentTimeMillis();
        if(tempLog == null || tempLog.size() == 0){
            currentReadingStartMillis = curr_time;
        }
        // TODO - change this to 5 min instead of 15 sec
        if((curr_time- currentReadingStartMillis )/1000 > 15){
            Log.e("ANDREI", "Saving current log and clearning log queue");
            saveLog(FlirCameraActivity.getInstance(),true);
            tempLog.clear();
        } else{
            try {
                assert tempLog != null;
                tempLog.put(curr_time, "Min: " + min + "; Max: " + max + "; Avg: " + avg);
            } catch (AssertionError e){
                e.printStackTrace();
            }
        }

        // Draw statistics to canvas
        paint.setTextSize(20 * ratiow);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText("Avg: " + avg + " " + thermalImage.getTemperatureUnit().toString().charAt(0), left * ratiow,(top -5) * ratioh,paint);
        paint.setColor(Color.RED);
        canvas.drawCircle((int)(mRect.getHotSpot().x*ratiow), (int)(mRect.getHotSpot().y*ratioh), 5 * ratiow, paint);
        canvas.drawText(max + " " + thermalImage.getTemperatureUnit().toString().charAt(0), mRect.getHotSpot().x * ratiow, (mRect.getHotSpot().y + 20)*ratioh, paint);
        paint.setColor(Color.BLUE);
        canvas.drawCircle(mRect.getColdSpot().x*ratiow, mRect.getColdSpot().y*ratioh, 5 * ratiow, paint);
        canvas.drawText(min+ " " + thermalImage.getTemperatureUnit().toString().charAt(0), mRect.getColdSpot().x * ratiow, (mRect.getColdSpot().y + 20)*ratioh, paint);
    }

    private void drawFaceRectangle(Bitmap dcBitmap, Bitmap msxBitmap){
        // Firebase FaceDetection
        Nv21Image mdcBitmapNv21Image = Nv21Image.bitmapToNV21(rs, dcBitmap);
        try {
            detectFaces(MainActivity.detector, msxBitmap, dcBitmap, imageFromBuffer(dcBitmap, ByteBuffer.wrap(mdcBitmapNv21Image.nv21ByteArray), getRotationCompensation("0", (MainActivity)mActivityContext, mContext)));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


/*
        // Native Face Detection
        // Convert Bitmap
        Bitmap mFaceBitmap = dcBitmap.copy(Bitmap.Config.RGB_565, true);
        FaceDetector faceDetector = new FaceDetector(mFaceBitmap.getWidth(), mFaceBitmap.getHeight(), 1);
        FaceDetector.Face[] faces = new FaceDetector.Face[1];
        int facesFound = faceDetector.findFaces(mFaceBitmap, faces);
                // Calculate Ratios
        float ratiow = (float) msxBitmap.getWidth() / (float) thermalImage.getWidth();
        float ratioh = (float) msxBitmap.getHeight() / (float) thermalImage.getHeight();
        float ratiow2 = (float) dcBitmap.getWidth() / (float) msxBitmap.getWidth();
        float ratioh2 = (float) dcBitmap.getHeight() / (float) msxBitmap.getHeight();

        if(facesFound > 0) {
            PointF midPoint = new PointF();
            faces[0].getMidPoint(midPoint);
            float confidence = faces[0].confidence();
            if(confidence >= 0.51) {      // At least 51% confidence that this is indeed a face
                // Calculate Face Detection Square
                float eyeDistance = faces[0].eyesDistance();
                float left2 = (midPoint.x - eyeDistance) / ratiow2;
                if (left2 < 0) {
                    left2 = 0.0f;
                }
                float top2 = (midPoint.y - eyeDistance) / ratioh2;
                if (top2 < 0) {
                    top2 = 0.0f;
                }
                float right2 = (midPoint.x + eyeDistance) / ratiow2;
                if (right2 > canvas.getWidth()) {
                    right2 = canvas.getWidth();
                }
                float bottom2 = (midPoint.y + eyeDistance) / ratioh2;
                if (bottom2 > canvas.getHeight()) {
                    bottom2 = canvas.getHeight();
                }

                // Paint Face Detection Square and Thermal Values to Canvas
                paint.setColor(Color.MAGENTA);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(left2, top2, right2, bottom2, paint);
                try {
                    // Calculate and draw Facial Detection Square values for Thermal Image (different resolution)
                    thermalImage.getMeasurements().addRectangle((int) (left2 / ratiow), (int) (top2 / ratioh), (int) ((right2 - left2) / ratiow), (int) ((bottom2 - top2) / ratioh));
                    MeasurementRectangle mRect2 = thermalImage.getMeasurements().getRectangles().get(1);
                    double avg2 = (Math.round((mRect2.getAverage().value) * 100.0) / 100.0);
                    paint.setTextSize(20 * ratiow);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawText("Avg: " + avg2 + " " + thermalImage.getTemperatureUnit().toString().charAt(0), left2, (top2 - 5), paint);
                } catch (Exception e) {
                    Log.e(TAG, "Could not calculate Face Detection square in Thermal Image");
                    e.printStackTrace();
                }
            }
        }
*/
    }

    private void detectFaces(FirebaseVisionFaceDetector detector, Bitmap msxBitmap, Bitmap dcBitmap, FirebaseVisionImage image) {
        detector.detectInImage(image)
                .addOnSuccessListener(
                        faces -> {
                            try {
                                Log.d(TAG, "detectFaces: Face Detected length: " + faces.size());
                                if (faces.isEmpty()) {
                                    framesBuffer.put(new BitmapFrameBuffer(msxBitmap, dcBitmap, null));
                                }
                                for (FirebaseVisionFace face : faces) {
                                    Rect bounds = face.getBoundingBox();
                                    Log.e("ASDF", "Face Bounds: " + bounds);
                                    final float rath = (float) image.getBitmap().getHeight() / (float) msxBitmap.getHeight();
                                    final float ratw = (float) image.getBitmap().getWidth() / (float) msxBitmap.getWidth();
                                    Rect rectangle = new Rect(
                                            (int) (bounds.left / ratw), // Left
                                            (int) (bounds.top / rath), // Top
                                            (int) (bounds.right / ratw), // Right
                                            (int) (bounds.bottom / rath) // Bottom
                                    );
                                    Log.e("ASDF", "Rect bounds: " + rectangle);

                                    framesBuffer.put(new BitmapFrameBuffer(msxBitmap, dcBitmap, rectangle));
                                    Log.d(TAG, "detectFaces: Face Detected: " + bounds.toString());
                                }
                            } catch (Exception e){
                                Log.e(TAG, "Could not detect face");
                                e.printStackTrace();
                            }
                        })
                .addOnFailureListener(
                        e -> {
                            Log.e(TAG, "Face Detection Error: " + e.getMessage());
                            e.printStackTrace();
                        });
    }

    static void saveLog(Context ctx, boolean shouldAppend) {
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

        FileWriter out;
        try {
            Date d = new Date(System.currentTimeMillis());
            String filename;
            if(shouldAppend){
                DateFormat formatter = new SimpleDateFormat("MM-dd-yyyy", Locale.US);
                filename = formatter.format(d);
                filename+="-FULL";
            } else{
                DateFormat formatter = new SimpleDateFormat("MM-dd-yyyy-HH:mm:ss", Locale.US);
                filename = formatter.format(d);
                filename+="-SHORT";
            }
            String path = Objects.requireNonNull(ctx.getExternalFilesDir("logs")).getAbsolutePath();
            out = new FileWriter(new File(path, filename),shouldAppend);
            out.write(msgLog.toString());
            out.close();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    static void resetLog() {
        CameraHandler.tempLog.clear();
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    /**
     * Get the angle by which an image must be rotated given the device's current
     * orientation.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static int getRotationCompensation(String cameraId, Activity activity, Context context)
            throws CameraAccessException {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int rotationCompensation = ORIENTATIONS.get(deviceRotation);

        // On most devices, the sensor orientation is 90 degrees, but for some
        // devices it is 270 degrees. For devices with a sensor orientation of
        // 270, rotate the image an additional 180 ((270 + 270) % 360) degrees.
        CameraManager cameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        int sensorOrientation;
        try {
            sensorOrientation = cameraManager
                    .getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SENSOR_ORIENTATION);
            rotationCompensation = (rotationCompensation + sensorOrientation + 270) % 360;
        } catch (NullPointerException ignored){
            return FirebaseVisionImageMetadata.ROTATION_0;
        }

        // Return the corresponding FirebaseVisionImageMetadata rotation value.
        int result;
        switch (rotationCompensation) {
            case 0:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                break;
            case 90:
                result = FirebaseVisionImageMetadata.ROTATION_90;
                break;
            case 180:
                result = FirebaseVisionImageMetadata.ROTATION_180;
                break;
            case 270:
                result = FirebaseVisionImageMetadata.ROTATION_270;
                break;
            default:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                Log.e(TAG, "Bad rotation value: " + rotationCompensation);
        }
        return result;
    }

    public static FirebaseVisionImage imageFromBuffer(Bitmap dcBitmap, ByteBuffer buffer, int rotation) {
        // [START set_metadata]
        FirebaseVisionImageMetadata metadata = new FirebaseVisionImageMetadata.Builder()
                .setWidth(dcBitmap.getWidth())   // 480x360 is typically sufficient for
                .setHeight(dcBitmap.getHeight())  // image recognition
                .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                .setRotation(rotation)
                .build();
        // [END set_metadata]
        // [START image_from_buffer]
        // [END image_from_buffer]
        return FirebaseVisionImage.fromByteBuffer(buffer, metadata);
    }

}
