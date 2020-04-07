/*******************************************************************
 * @title FLIR THERMAL SDK
 * @file FrameDataHolder.java
 * @Author FLIR Systems AB
 *
 * @brief Container class that holds references to Bitmap images
 *
 * Copyright 2019:    FLIR Systems
 ********************************************************************/

package com.samples.flironecamera;

import android.graphics.Bitmap;

class FrameDataHolder {

    public final Bitmap msxBitmap;
    public final Bitmap dcBitmap;

    FrameDataHolder(Bitmap msxBitmap, Bitmap dcBitmap){
        this.msxBitmap = msxBitmap;
        this.dcBitmap = dcBitmap;
    }
}
