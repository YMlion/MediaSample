package com.ymlion.mediasample.capture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
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
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
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
    private int[] textureBuffer;
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
            Rect rect = holder.getSurfaceFrame();
            mDisplayWidth = rect.width();
            mDisplayHeight = rect.height();
            texture = new SurfaceTexture(10);
            mCamera = Camera.open(mCameraFacing);
            Parameters parameters = mCamera.getParameters();
            mPictureSize = Collections.max(parameters.getSupportedPictureSizes(),
                    new CompareSizesByArea());
            parameters.setPictureSize(mPictureSize.width, mPictureSize.height);
            Log.i(TAG, "picture size: " + mPictureSize.width + "*" + mPictureSize.height);
            mPreviewSize =
                    chooseOptimalSize(parameters.getSupportedPreviewSizes(), wantedMinPreviewWidth,
                            mPictureSize);
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            Log.i(TAG, "preview size: " + mPreviewSize.width + "*" + mPreviewSize.height);
            if (null != mCallback) {
                mCallback.onInitFinished(mPreviewSize.width, mPreviewSize.height);
            }
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
            textureBmp =
                    Bitmap.createBitmap(mDisplayWidth, mDisplayHeight, Bitmap.Config.ARGB_8888);
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
        return drawByArgb(data);
    }

    private boolean drawByBitmap(byte[] data) {
        long s = System.currentTimeMillis();
        Size size = mCamera.getParameters().getPreviewSize();
        //这里一定要得到系统兼容的大小，否则解析出来的是一片绿色或者其他
        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, outputStream);
        long s1 = System.currentTimeMillis() - s;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;//必须设置为565，否则无法检测
        byte[] bytes = outputStream.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
        long s2 = System.currentTimeMillis() - s;
        Matrix matrix = new Matrix();
        if (mCameraFacing == getCameraFacing(FRONT) && mOrientation == 90) {
            matrix.setScale(-1, 1);
            //matrix.postRotate(270);
        }
        matrix.postRotate(mOrientation);

        float sx = mDisplayWidth / 1.0f / bitmap.getWidth();
        float sy = mDisplayHeight / 1.0f / bitmap.getHeight();
        if (mOrientation == 90 || mOrientation == 270) {
            matrix.postScale(sy, sx);
        } else {
            matrix.postScale(sx, sy);
        }
        Bitmap bm = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix,
                true);
        long s3 = System.currentTimeMillis() - s;
        Canvas canvas = mSurface.lockCanvas(null);
        canvas.drawBitmap(bm, 0, 0, null);
        mSurface.unlockCanvasAndPost(canvas);
        Log.d(TAG, "handleMessage: "
                + s1
                + " ; "
                + s2
                + " ; "
                + s3
                + " ; "
                + (System.currentTimeMillis() - s));
        return true;
    }

    private boolean drawByArgb(byte[] data) {
        long s = System.currentTimeMillis();
        long e, t;
        Size size = mCamera.getParameters().getPreviewSize();
        int previewSize = size.width * size.height;
        byte[] rgbaData = new byte[previewSize * 4];
        YuvUtil.convertToRgba(data, size.width, size.height, rgbaData, 1);
        e = System.currentTimeMillis();
        long s1 = e - s;
        byte[] rotateBytes;
        if (mOrientation > 0) {
            rotateBytes = new byte[previewSize * 4];
            YuvUtil.rotateARGB(rgbaData, rotateBytes, size.width, size.height, mOrientation);
        } else {
            rotateBytes = rgbaData;
        }
        t = System.currentTimeMillis();
        long s2 = t - e;
        e = t;
        int displaySize = mDisplayWidth * mDisplayHeight;
        byte[] dst = new byte[displaySize * 4];
        int w, h;
        if (mOrientation == 90 || mOrientation == 270) {
            w = size.height;
            h = size.width;
        } else {
            w = size.width;
            h = size.height;
        }
        YuvUtil.scaleARGB(rotateBytes, w, h, dst, mDisplayWidth, mDisplayHeight, 0);
        t = System.currentTimeMillis();
        long s3 = t - e;
        e = t;
        ByteBuffer buffer = ByteBuffer.allocate(displaySize * 4);
        buffer.put(dst);
        buffer.rewind();
        textureBmp.copyPixelsFromBuffer(buffer);
        t = System.currentTimeMillis();
        long s4 = t - e;
        e = t;
        Canvas canvas = mSurface.lockCanvas(null);
        canvas.drawBitmap(textureBmp, 0, 0, null);
        mSurface.unlockCanvasAndPost(canvas);
        t = System.currentTimeMillis();
        long s5 = t - e;
        e = t - s;
        Log.d(TAG, "handleMessage: "
                + mOrientation
                + "; "
                + textureBmp.getWidth()
                + "x"
                + textureBmp.getHeight()
                + "; "
                + s1
                + " ; "
                + s2
                + " ; "
                + s3
                + " ; "
                + s4
                + " ; " + s5 + " ; total = " + e);
        return true;
    }

    private boolean drawByYuv(byte[] data) {
        long s = System.currentTimeMillis();
        Size size = mCamera.getParameters().getPreviewSize();
        int displaySize = mDisplayWidth * mDisplayHeight;
        byte[] dst = new byte[displaySize * 3 / 2];
        YuvUtil.scaleNV21(data, size.width, size.height, dst, mDisplayWidth, mDisplayHeight, 1);
        long s1 = System.currentTimeMillis() - s;
        byte[] rgbaData = new byte[displaySize * 4];
        YuvUtil.convertToRgba(dst, mDisplayWidth, mDisplayHeight, rgbaData, 1);
        long s2 = System.currentTimeMillis() - s;
        ByteBuffer buffer = ByteBuffer.allocate(displaySize * 4);
        buffer.put(rgbaData);
        buffer.rewind();
        textureBmp.copyPixelsFromBuffer(buffer);
        long s3 = System.currentTimeMillis() - s;
        Canvas canvas = mSurface.lockCanvas(null);
        canvas.drawBitmap(textureBmp, 0, 0, null);
        mSurface.unlockCanvasAndPost(canvas);
        Log.d(TAG, "handleMessage: "
                + s1
                + " ; "
                + s2
                + " ; "
                + s3
                + " ; "
                + (System.currentTimeMillis() - s));
        return true;
    }

    public int[] yuv2rgb(byte[] data, int width, int height) {
        int frameSize = width * height;
        int[] rgba = textureBuffer;
        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++) {
                int y = (0xff & ((int) data[i * width + j]));
                int u = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 0]));
                int v = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 1]));
                y = y < 16 ? 16 : y;
                int r = Math.round(1.164f * (y - 16) + 1.596f * (v - 128));
                int g = Math.round(1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                int b = Math.round(1.164f * (y - 16) + 2.018f * (u - 128));
                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);
                rgba[i * width + j] = 0xff000000 + (b << 16) + (g << 8) + r;
            }
        return rgba;
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

    private Size chooseOptimalSize(List<Size> choices, int wantedMinWidth, Size aspectRatio) {
        List<Size> results = new ArrayList<Size>();
        for (Size choice : choices) {
            if (choice.width * aspectRatio.height == choice.height * aspectRatio.width
                    && choice.height >= wantedMinWidth) {
                results.add(choice);
            }
        }

        if (results.size() > 0) {
            return Collections.min(results, new CompareSizesByArea());
        } else {
            Log.e(TAG, "failed to any suitable preview size");
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
