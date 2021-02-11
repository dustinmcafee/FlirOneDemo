package com.example.flirone;

import android.graphics.Bitmap;
import android.graphics.Rect;

class BitmapFrameBuffer {
    final Bitmap msxBitmap;
    final Bitmap dcBitmap;
    final Rect faceRect;
    BitmapFrameBuffer(Bitmap msxBitmap, Bitmap dcBitmap, Rect faceRect){
        this.msxBitmap = msxBitmap;
        this.dcBitmap = dcBitmap;
        this.faceRect = faceRect;
    }
}
