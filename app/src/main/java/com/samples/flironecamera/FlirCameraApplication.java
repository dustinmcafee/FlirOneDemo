package com.samples.flironecamera;

import android.app.Application;

import com.flir.thermalsdk.live.Identity;

public class FlirCameraApplication extends Application {
    public static CameraHandler cameraHandler;
    public static Identity connectedIdentity;
}
