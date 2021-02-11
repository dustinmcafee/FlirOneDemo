package com.example.flirone;

import android.app.Application;

import com.flir.thermalsdk.live.Identity;
import com.google.firebase.FirebaseApp;

public class FlirCameraApplication extends Application {
    public static CameraHandler cameraHandler;
    public static Identity connectedCameraIdentity;
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(getApplicationContext());
    }

}
