package com.jgraves.colorrecognizer.app;

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import java.io.IOException;

public class MainActivity extends Activity {
    private Preview mPreview;

    private static int screenWidth;
    private static int screenHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        mPreview = (Preview) findViewById(R.id.surface);
        mPreview.setProgressWheel((ProgressWheel) findViewById(R.id.pw_spinner));
        setScreenDimensions();
    }

    private void setScreenDimensions() {
        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        screenHeight = display.getHeight();
        screenWidth = display.getWidth();
    }
}


class Preview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
    SurfaceHolder mHolder;
    Camera mCamera;
    int mCameraWidth;
    int mCameraHeight;
    int out[];
    private static ProgressWheel mProgressWheel;
    private BackgroundTask mBackgroundTask;

    private void init() {
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mBackgroundTask = new BackgroundTask();

    }

    public void setProgressWheel(ProgressWheel pw){
        mProgressWheel = pw;
    }

    public Preview(Context context) {
        super(context);
        init();
    }

    public Preview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Preview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        try {
            mProgressWheel.invalidate();
            out = convertYUV420_NV21toARGB8888(data, mCameraWidth, mCameraHeight);
        } catch (Exception ex) {
            Log.i("App", "Exception: " + ex.getMessage());
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        mProgressWheel.bringToFront();
        mCamera = Camera.open();
        Camera.Parameters p = mCamera.getParameters();
        Camera.Size size = p.getPreviewSize();
        mCameraWidth = size.width;
        mCameraHeight = size.height;
        out = new int[mCameraWidth * mCameraHeight];
        Log.i("App", "Camera params - height: " + mCameraHeight + ", width: " + mCameraWidth);
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException exception) {
            mCamera.release();
            mCamera = null;
            Log.e("ColorRecognizer", "Exception: " + exception.getMessage());
        }
        mCamera.setPreviewCallback(this);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        this.getHolder().removeCallback(this);
        mHolder.removeCallback(this);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(w, h);
        mCamera.setParameters(parameters);
        mCamera.startPreview();
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                if (!mBackgroundTask.isCancelled())
                    mBackgroundTask.cancel(true);
                mBackgroundTask = (BackgroundTask)new BackgroundTask().execute();
                float x = event.getX();
                float y = event.getY();
                // do something
                mProgressWheel.setX(x - mProgressWheel.getWidth()/2);
                mProgressWheel.setY(y - mProgressWheel.getHeight()/2);
                mProgressWheel.invalidate();
                mProgressWheel.setVisibility(VISIBLE);
               //  mProgressWheel.incrementProgress();
                break;
            case MotionEvent.ACTION_UP:
                mBackgroundTask.cancel(true);
                // do Something
                mProgressWheel.setVisibility(INVISIBLE);
                break;
        }

        float x = event.getX();
        float y = event.getY();
        int index = (int) (x * mCameraWidth + y);
        Log.i("ColorRecognizer", "onTouch, x: " + x + " y:" + y);
        Log.i("ColorRecognizer", "onTouch - index: " + index);
        int color = out[index];
        Log.i("ColorRecognizer", "onTouch - color: " + Integer.toHexString(out[index]));
        return true;
    }
    /**
     * Converts YUV420 NV21 to ARGB8888
     *
     * @param data byte array on YUV420 NV21 format.
     * @param width pixels width
     * @param height pixels height
     * @return a ARGB8888 pixels int array. Where each int is a pixels ARGB.
     */
    public static int[] convertYUV420_NV21toARGB8888(byte [] data, int width, int height) {
        int size = width*height;
        int offset = size;
        int[] pixels = new int[size];
        int u, v, y1, y2, y3, y4;
        // i along Y and the final pixels
        // k along pixels U and V
        for(int i=0, k=0; i < size; i+=2, k+=2) {
            y1 = data[i  ]&0xff;
            y2 = data[i+1]&0xff;
            y3 = data[width+i  ]&0xff;
            y4 = data[width+i+1]&0xff;

            v = data[offset+k  ]&0xff;
            u = data[offset+k+1]&0xff;
            v = v-128;
            u = u-128;

            pixels[i  ] = convertYUVtoARGB(y1, u, v);
            pixels[i+1] = convertYUVtoARGB(y2, u, v);
            pixels[width+i  ] = convertYUVtoARGB(y3, u, v);
            pixels[width+i+1] = convertYUVtoARGB(y4, u, v);

            if (i!=0 && (i+2)%width==0)
                i += width;
        }
        return pixels;
    }

    private static int convertYUVtoARGB(int y, int u, int v) {
        int r = y + (int)(1.772f*v);
        int g = y - (int)(0.344f*v + 0.714f*u);
        int b = y + (int)(1.402f*u);
        r = r>255? 255 : r<0 ? 0 : r;
        g = g>255? 255 : g<0 ? 0 : g;
        b = b>255? 255 : b<0 ? 0 : b;
        return 0xff000000 | (r<<16) | (g<<8) | b;
    }

    class BackgroundTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            publishProgress();
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... params) {
            mProgressWheel.incrementProgress();
        }
    }

}
