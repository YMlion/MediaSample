package com.ymlion.mediasample.capture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import com.ymlion.mediasample.util.YuvUtil;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("deprecation") public class CameraCapture
        implements Camera.FaceDetectionListener, Handler.Callback {
    private static final String TAG = CameraCapture.class.getSimpleName();
    public static final int FLASH_MODE_OFF = 0;
    public static final int FLASH_MODE_ON = 1;
    public static final int FLASH_MODE_AUTO = 2;
    public static final int FRONT = 1;
    public static final int BACK = 2;

    private Size mPictureSize;
    private Size mPreviewSize;

    private int mFlashMode = FLASH_MODE_AUTO;
    private boolean mIsAutoFocusSupport;

    private int mCameraFacing = CameraInfo.CAMERA_FACING_BACK;
    private Camera mCamera;

    private ICameraCallback mCallback;

    private WeakReference<Context> mContext;
    private SurfaceHolder mSurface;
    private Paint mPaint;
    private SurfaceHolder mFaceSurface;
    private SurfaceTexture texture;
    private Handler mHandler;
    private byte[] gBuffer;
    private Bitmap textureBmp;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mOrientation;

    public CameraCapture(Context context) {
        mContext = new WeakReference<Context>(context);
        HandlerThread thread = new HandlerThread("surface");
        thread.start();
        mHandler = new Handler(thread.getLooper(), this);
    }

    public void setCameraType(int cameraType) {
        mCameraFacing = getCameraFacing(cameraType);
    }

    public void changeFacing() {
        if (mCameraFacing == getCameraFacing(FRONT)) {
            setCameraType(BACK);
        } else {
            setCameraType(FRONT);
        }
    }

    public void setCameraCallback(ICameraCallback callback) {
        mCallback = callback;
    }

    public void open(SurfaceHolder holder, int wantedMinPreviewWidth) {
        if (null == mContext.get()) {
            return;
        }

        if (null != mCamera) {
            close();
        }

        try {
            mSurface = holder;
            texture = new SurfaceTexture(10);
            mCamera = Camera.open(mCameraFacing);
            Parameters parameters = mCamera.getParameters();
            mPictureSize = Collections.max(parameters.getSupportedPictureSizes(),
                    new CompareSizesByArea());
            parameters.setPictureSize(mPictureSize.width, mPictureSize.height);
            Log.i(TAG, "picture size: " + mPictureSize.width + "*" + mPictureSize.height);
            int scaleMode = 1;// 4:3
            if (mPictureSize.width * 9 == mPictureSize.height * 16) {
                scaleMode = 2;// 16:9
            }
            mPreviewSize =
                    chooseOptimalSize(parameters.getSupportedPreviewSizes(), wantedMinPreviewWidth,
                            mPictureSize, scaleMode);
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            Log.i(TAG, "preview size: " + mPreviewSize.width + "*" + mPreviewSize.height);
            if (null != mCallback) {
                mCallback.onInitFinished(mPreviewSize.width, mPreviewSize.height);
            }
            Rect rect = holder.getSurfaceFrame();
            mDisplayWidth = rect.width();
            mDisplayHeight = mDisplayWidth * mPreviewSize.width / mPreviewSize.height;
            Log.i(TAG, "display size is " + mDisplayWidth + "x" + mDisplayHeight);
            setupFlashMode(parameters);
            setupFocusMode(parameters);
            parameters.setPreviewFormat(ImageFormat.NV21);
            mCamera.setParameters(parameters);
            mOrientation = determineDisplayOrientation();
            Log.i(TAG, "orientation: " + mOrientation);
            if (mOrientation > 0) {
                mCamera.setDisplayOrientation(mOrientation);
            }
            /*if (mOrientation == 90 || mOrientation == 270) {
                int tmp = mDisplayHeight;
                mDisplayHeight = mDisplayWidth;
                mDisplayWidth = tmp;
                Log.w(TAG, "width and height exchanged!!!");
            }*/
            //mCamera.setPreviewDisplay(mSurface);
            mCamera.setPreviewTexture(texture);
            int bufferSize = mPreviewSize.width * mPreviewSize.height;
            //textureBmp =
            //        Bitmap.createBitmap(mDisplayWidth, mDisplayHeight, Bitmap.Config.ARGB_8888);
            bufferSize =
                    2 * bufferSize * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
            gBuffer = new byte[bufferSize];
            mCamera.addCallbackBuffer(gBuffer);
            mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                @Override public void onPreviewFrame(byte[] data, Camera camera) {
                    Message message = new Message();
                    message.obj = data;
                    mHandler.sendMessage(message);
                    if (closed) {
                        return;
                    }
                    mCamera.addCallbackBuffer(gBuffer);
                }
            });
            mCamera.startPreview();
            closed = false;
            // TODO: 2017/9/15 face detect
            /*if (mCamera.getParameters().getMaxNumDetectedFaces() > 0) {
                mCamera.setFaceDetectionListener(this);
                mCamera.startFaceDetection();
            }*/
        } catch (Exception e) {
            Log.e(TAG, "failed to open camera: " + mCameraFacing);
            closed = true;
        }
    }

    @Override public boolean handleMessage(Message msg) {
        if (closed) {
            return false;
        }
        byte[] data = (byte[]) msg.obj;
        return YuvUtil.drawByArgb(mCamera, mOrientation, mDisplayWidth, mDisplayHeight,
                mSurface.getSurface(), data);
    }

    private boolean closed = false;

    public void close() {
        closed = true;
        if (null != mCamera) {
            /*if (mCamera.getParameters().getMaxNumDetectedFaces() > 0) {
                mCamera.stopFaceDetection();
            }*/
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            texture.release();
            texture = null;
        }
    }

    public void setFlashMode(int flashMode) {
        if (mFlashMode == flashMode) {
            return;
        }
        mFlashMode = flashMode;
        if (null != mCamera) {
            Parameters parameters = mCamera.getParameters();
            setupFlashMode(parameters);
            mCamera.setParameters(parameters);
        }
    }

    public void takePicture() {
        Log.i(TAG, "takePicture()");
        if (null == mCamera) {
            return;
        }

        if (mIsAutoFocusSupport) {
            mCamera.autoFocus(mAutoFocusCallback);
        } else {
            mCamera.takePicture(null, null, mPictureCallback);
        }
    }

    private int getCameraFacing(int cameraType) {
        return (FRONT == cameraType) ? CameraInfo.CAMERA_FACING_FRONT
                : CameraInfo.CAMERA_FACING_BACK;
    }

    /**
     * @param scaleMode 4:3 or 16:9
     */
    private Size chooseOptimalSize(List<Size> choices, int wantedMinWidth, Size aspectRatio,
            int scaleMode) {
        List<Size> results = new ArrayList<Size>();
        for (Size choice : choices) {
            if (choice.width * aspectRatio.height == choice.height * aspectRatio.width
                    && choice.height >= wantedMinWidth) {
                results.add(choice);
            }
            Log.d(TAG, "support preview size : " + choice.width + " x " + choice.height);
        }

        if (results.size() > 0) {
            return Collections.min(results, new CompareSizesByArea());
        } else {
            Log.e(TAG, "failed to any suitable preview size");
            if (scaleMode == 1) {
                for (Size choice : choices) {
                    if (choice.width * 3 == choice.height * 4) {
                        return choice;
                    }
                }
            } else if (scaleMode == 2) {
                for (Size choice : choices) {
                    if (choice.width * 9 == choice.height * 16) {
                        return choice;
                    }
                }
            }
            return choices.get(0);
        }
    }

    private void setupFlashMode(Parameters parameters) {
        List<String> flashModes = parameters.getSupportedFlashModes();
        if (null == flashModes) {
            return;
        }

        String flashMode = Parameters.FLASH_MODE_AUTO;
        if (FLASH_MODE_OFF == mFlashMode) {
            flashMode = Parameters.FLASH_MODE_OFF;
        } else if (FLASH_MODE_ON == mFlashMode) {
            flashMode = Parameters.FLASH_MODE_ON;
        }
        parameters.setFlashMode(flashMode);
    }

    private void setupFocusMode(Parameters parameters) {
        mIsAutoFocusSupport = false;
        List<String> choices = parameters.getSupportedFocusModes();
        if (choices.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mIsAutoFocusSupport = true;
            parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (choices.contains(Parameters.FOCUS_MODE_AUTO)) {
            mIsAutoFocusSupport = true;
            parameters.setFocusMode(Parameters.FLASH_MODE_AUTO);
        } else if (choices.contains(Parameters.FOCUS_MODE_MACRO)) {
            mIsAutoFocusSupport = true;
            parameters.setFocusMode(Parameters.FOCUS_MODE_MACRO);
        }
        Log.i(TAG, "auto focus: " + mIsAutoFocusSupport);
    }

    private int determineDisplayOrientation() {
        Context context = mContext.get();
        if (context == null) {
            Log.e(TAG, "context has been destroyed");
            return 0;
        }

        CameraInfo cameraInfo = new CameraInfo();
        Camera.getCameraInfo(mCameraFacing, cameraInfo);

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }

        return result;
    }

    private final PictureCallback mPictureCallback = new PictureCallback() {
        @Override public void onPictureTaken(byte[] data, Camera camera) {
            Log.i(TAG, "onPictureTaken()");
            camera.stopPreview();
            if (null != mCallback) {
                mCallback.onImageAvailable(data);
            }
            camera.cancelAutoFocus();
            mCamera.startPreview();
        }
    };

    private final AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback() {
        @Override public void onAutoFocus(boolean success, Camera camera) {
            Log.i(TAG, "onAutoFocus(): " + success);
            camera.takePicture(null, null, mPictureCallback);
        }
    };

    @Override public void onFaceDetection(Camera.Face[] faces, Camera camera) {
        if (faces.length < 1) {
            return;
        }
        Canvas canvas = mFaceSurface.lockCanvas();//锁定Surface 并拿到Canvas
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);// 清除上一次绘制
        Matrix matrix = new Matrix();

        //        这里使用的是后置摄像头就不用翻转。由于没有进行旋转角度的兼容，这里直接传系统调整的值
        prepareMatrix(matrix, mCameraFacing == getCameraFacing(FRONT), mOrientation, mDisplayWidth,
                mDisplayHeight);

        //        canvas.save();
        //        由于有的时候手机会存在一定的偏移（歪着拿手机）所以在这里需要旋转Canvas 和 matrix，
        //        偏移值从OrientationEventListener获得，具体Google
        //        canvas.rotate(-degrees); 默认是逆时针旋转
        //        matrix.postRotate(degrees);默认是顺时针旋转
        for (int i = 0; i < faces.length; i++) {
            RectF rect = new RectF(faces[i].rect);
            Log.d(TAG, "onFaceDetection: " + rect);
            matrix.mapRect(rect);//应用到rect上
            Log.i(TAG, "onFaceDetection: " + rect);
            canvas.drawRect(rect, mPaint);
        }
        mFaceSurface.unlockCanvasAndPost(canvas);//更新Canvas并解锁
    }

    /**
     * 该方法出自
     * http://blog.csdn.net/yanzi1225627/article/details/38098729/
     * http://bytefish.de/blog/face_detection_with_android/
     *
     * @param matrix 这个就不用说了
     * @param mirror 是否需要翻转，后置摄像头（手机背面）不需要翻转，前置摄像头需要翻转。
     * @param displayOrientation 旋转的角度
     * @param viewWidth 预览View的宽高
     */
    public void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation, int viewWidth,
            int viewHeight) {
        // Need mirror for front camera.
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height)
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }

    public void setFaceHolder(SurfaceHolder holder) {
        mFaceSurface = holder;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.RED);
        mPaint.setStrokeWidth(5);
        mPaint.setStyle(Paint.Style.STROKE);
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.width * lhs.height - (long) rhs.width * rhs.height);
        }
    }
}
