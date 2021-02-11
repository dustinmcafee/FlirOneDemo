package com.example.flirone;

import android.graphics.Bitmap;

class BitmapFrameBuffer {
    final Bitmap msxBitmap;
    final Bitmap dcBitmap;
    BitmapFrameBuffer(Bitmap msxBitmap, Bitmap dcBitmap){
        this.msxBitmap = msxBitmap;
        this.dcBitmap = dcBitmap;
    }
}
