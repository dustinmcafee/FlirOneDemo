package com.elotouch.flirone;

import android.content.Context;
import android.util.Log;

import com.flir.thermalsdk.image.ThermalImage;

import java.io.File;
import java.io.IOException;

/**
 * Provide a directory where camera imported images files can be saved
 */
class FileHandler {
    private static File filesDir = null;
    private final static String TAG = "FileHandler";

    public FileHandler(Context applicationContext) {
        filesDir = applicationContext.getFilesDir();
    }

    public String getImageStoragePathStr() {
        return filesDir.getAbsolutePath();
    }

    public File getImageStoragePath() {
        return filesDir;
    }

    public void saveImage(ThermalImage thermalImage, String fileName){
        try {
            thermalImage.saveAs(filesDir.getAbsolutePath() + "/" + fileName);
        } catch (IOException e){
            Log.e(TAG, "Can't save file " + filesDir.getAbsolutePath() + "/" + fileName);
            e.printStackTrace();
        }
    }
}
