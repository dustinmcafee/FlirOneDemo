package com.samples.flironecamera;

import android.app.Application;

import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.live.Identity;

import java.util.concurrent.LinkedBlockingQueue;

public class FlirCameraContext extends Application {
    public static CameraHandler cameraHandler;
    public static Identity connectedIdentity;
}
