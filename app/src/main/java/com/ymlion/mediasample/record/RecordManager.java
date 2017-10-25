package com.ymlion.mediasample.record;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import com.ymlion.mediasample.util.CodecCallback;
import com.ymlion.mediasample.util.ImageUtil;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
import static android.hardware.camera2.CameraMetadata.CONTROL_MODE_AUTO;

/**
 * Created by YMlion on 2017/7/26.
 */

public class RecordManager {

    private static final String TAG = "RecordManager";
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 180);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;
    private Size mPreviewSize;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_RECORDING = 5;
    private static final int STATE_RECORDED = 6;

    /**
     * @see #STATE_PREVIEW
     * @see #STATE_RECORDING
     * @see #STATE_RECORDED
     */
    private int mState = STATE_PREVIEW;

    private static CameraManager cm;
    private Context mContext;
    private SurfaceTexture mTexture;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private Handler mThreadHandler;
    private String mCameraId;
    private CaptureCallback mCaptureCallback;

    private Surface mPreviewSurface;

    private int mFacing = 1;

    private SensorManager sm;
    private SensorEventListener mSensorListener;
    private float mSensorX;
    private float mSensorY;

    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private Surface inputSurface;
    private MediaMuxer muxer;
    private int videoTrack = -1;
    private int audioTrack = -1;
    private ImageReader imageReader;
    private AudioRecord audioRecord;
    private int audioBufferSize;
    private Handler videoHandler;
    private Handler audioHandler;
    private boolean recordStop = false;
    private RecordListener recordListener;

    public RecordManager(Context context, SurfaceTexture surfaceTexture) {
        this.mContext = context;
        cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.mTexture = surfaceTexture;
        HandlerThread thread = new HandlerThread("RecordManager");
        thread.start();
        mThreadHandler = new Handler(thread.getLooper());
    }

    /**
     * 设置摄像头：前置or后置
     *
     * @param facing 0：前置；1：后置
     */
    public void setFacing(int facing) {
        mFacing = facing;
    }

    /**
     * 切换摄像头
     *
     * @param textureView preview surface
     */
    public void changeCamera(TextureView textureView) {
        mFacing = 1 - mFacing;
        close();
        open(textureView.getWidth(), textureView.getHeight());
    }

    public void open(int width, int height) {
        try {
            setUpCameraOutputs(width, height);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (mContext.checkSelfPermission(Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            cm.openCamera(mCameraId, new DeviceStateCallback(), mThreadHandler);
            Sensor gravitySensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sm.registerListener(mSensorListener = new CameraSensorListener(), gravitySensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startRecord() {
        Log.d(TAG, "startRecord");
        mState = STATE_RECORDING;
        recordStop = false;
        setupRecord();
        try {
            inputSurface = videoEncoder.createInputSurface();
            List<Surface> surfaces = Arrays.asList(mPreviewSurface, inputSurface);
            mCameraDevice.createCaptureSession(surfaces, new SessionStateCallback(),
                    mThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupRecord() {
        try {
            muxer = new MediaMuxer(getFile(1).getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            setupVideoEncoder();
            setupAudioEncoder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupAudioEncoder() throws IOException {
        audioTrack = -1;
        int sampleRate = 48000;   //采样率，默认48k
        int channelCount = 2;     //音频采样通道，默认2通道
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;        //通道设置，默认立体声
        final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;     //设置采样数据格式，默认16比特PCM
        final String mime = "audio/mp4a-latm";    //录音编码的mime
        int rate = 128000;                    //编码的key bit rate

        audioBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2;

        //实例化AudioRecord
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                audioFormat, audioBufferSize);

        MediaFormat format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, rate);
        audioEncoder = MediaCodec.createEncoderByType(mime);
        audioEncoder.setCallback(new CodecCallback() {
            @Override public void onInputBufferAvailable(MediaCodec codec, int index) {
                sendMsg(1, index, null, audioHandler);
            }

            @Override public void onOutputBufferAvailable(MediaCodec codec, int index,
                    MediaCodec.BufferInfo info) {
                sendMsg(2, index, info, audioHandler);
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, @Nullable MediaFormat format) {
                if (format == null) {
                    format = audioEncoder.getOutputFormat();
                }
                audioTrack = muxer.addTrack(format);
                Log.d(TAG, "audio track is " + audioTrack);
                if (recordListener != null) {
                    recordListener.onAudioFormatChanged(format);
                }
                if (videoTrack >= 0) {
                    muxer.start();
                }
            }
        });
        if (audioHandler == null) {
            audioHandler = createHandler("audio", new Handler.Callback() {
                long startTime = 0L;

                @Override public boolean handleMessage(Message msg) {
                    if (recordStop) {
                        startTime = 0;
                        return false;
                    }
                    int what = msg.what;
                    int index = msg.arg1;
                    if (what == 1) {
                        ByteBuffer buffer = audioEncoder.getInputBuffer(index);
                        int readLength = audioRecord.read(buffer, audioBufferSize);
                        if (readLength > 0) {
                            if (startTime == 0) {
                                startTime = System.nanoTime() / 1000;
                            }
                            long presentationTimeUs = System.nanoTime() / 1000 - startTime;
                            audioEncoder.queueInputBuffer(index, 0, readLength, presentationTimeUs,
                                    0);
                        }
                        return true;
                    } else if (what == 2) {
                        MediaCodec.BufferInfo info = (MediaCodec.BufferInfo) msg.obj;
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            startTime = 0;
                            return true;
                        }
                        if (index >= 0 && videoTrack >= 0) {
                            ByteBuffer buffer = audioEncoder.getOutputBuffer(index);
                            muxer.writeSampleData(audioTrack, buffer, info);
                        }
                        audioEncoder.releaseOutputBuffer(index, false);
                        return true;
                    }
                    return false;
                }
            });
        }
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void setupVideoEncoder() throws IOException {
        videoTrack = -1;
        String mime = "video/avc";    //编码的MIME
        int rate = 3200000;            //波特率，12800kb
        int frameRate = 30;           //帧率，30帧
        int frameInterval = 1;        //关键帧一秒一关键帧

        MediaFormat format = MediaFormat.createVideoFormat(mime, 720, 480);
        format.setInteger(MediaFormat.KEY_BIT_RATE, rate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, frameInterval);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoEncoder = MediaCodec.createEncoderByType("video/avc");
        videoEncoder.setCallback(new CodecCallback() {
            @Override public void onOutputBufferAvailable(MediaCodec codec, int index,
                    MediaCodec.BufferInfo info) {
                sendMsg(1, index, info, videoHandler);
            }

            @Override public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                Log.d(TAG, "onOutputFormatChanged: " + format);
                if (format == null) {
                    format = videoEncoder.getOutputFormat();
                }
                videoTrack = muxer.addTrack(format);
                Log.d(TAG, " video track is " + videoTrack);
                if (recordListener != null) {
                    recordListener.onVideoFormatChanged(format);
                }
                if (audioTrack >= 0) {
                    muxer.start();
                }
            }
        });
        if (videoHandler == null) {
            videoHandler = createHandler("video", new Handler.Callback() {
                long startTime = 0L;

                @Override public boolean handleMessage(Message msg) {
                    int what = msg.what;
                    if (what == 1) {
                        int index = msg.arg1;
                        MediaCodec.BufferInfo info = (MediaCodec.BufferInfo) msg.obj;
                        //Log.d(TAG, "info flags is " + info.flags + "; index is " + index);
                        if (index >= 0 && audioTrack >= 0) {
                            if (startTime == 0) {
                                startTime = info.presentationTimeUs;
                            }
                            if (info.presentationTimeUs > 0) {
                                info.presentationTimeUs -= startTime;
                                ByteBuffer buffer = videoEncoder.getOutputBuffer(index);
                                muxer.writeSampleData(videoTrack, buffer, info);
                                if (recordListener != null) {
                                    byte[] bytes = new byte[info.size];
                                    buffer.get(bytes);
                                    recordListener.onVideoFrame(bytes, info.presentationTimeUs);
                                }
                                // todo 直接写入文件的是未封装的h264数据
                                /*byte[] bytes = new byte[info.size];
                                buffer.get(bytes);
                                    try {
                                    out.write(bytes);
                                    out.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }*/
                            }
                        }
                        videoEncoder.releaseOutputBuffer(index, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            recordStop = true;
                            videoEncoder.stop();
                            inputSurface.release();
                            videoEncoder.release();
                            audioEncoder.stop();
                            audioEncoder.release();
                            audioRecord.stop();
                            audioRecord.release();
                            muxer.stop();
                            muxer.release();
                        }
                        return true;
                    }
                    return false;
                }
            });
        }
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void sendMsg(int what, int index, Object obj, Handler handler) {
        if (recordStop) {
            return;
        }
        Message msg = new Message();
        msg.what = what;
        msg.arg1 = index;
        msg.obj = obj;
        handler.sendMessage(msg);
    }

    private Handler createHandler(String name, Handler.Callback callback) {
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new Handler(thread.getLooper(), callback);
    }

    public void stopRecord() {
        Log.d(TAG, "stopRecord");
        mState = STATE_RECORDED;
        videoEncoder.signalEndOfInputStream();
        try {
            List<Surface> surfaces = Collections.singletonList(mPreviewSurface);
            //List<Surface> surfaces = Collections.singletonList(imageReader.getSurface());
            mCameraDevice.createCaptureSession(surfaces, new SessionStateCallback(),
                    mThreadHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
        }
        sm.unregisterListener(mSensorListener);
        if (imageReader != null) {
            imageReader.close();
        }
    }

    private void setupImageReader() {
        // TODO: 2017/9/6 同样可以使用ImageReader设置回调来获取每一帧数据，然后将数据放入codec的buffer中进行编码
        // FIXME: 2017/9/7 使用ImageReader获取每一帧数据特别卡顿，暂时没有找到解决方法。
        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888/*PixelFormat.RGBX_8888*/,
                1);
        HandlerThread thread = new HandlerThread("ImageReader");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        final Paint paint = new Paint();
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                Log.e(TAG, "onImageAvailable: image format is " + image.getFormat());
                Bitmap bm = ImageUtil.INSTANCE.getBitmap(image);
                if (bm == null) {
                    Log.e(TAG, "onImageAvailable: null");
                    return;
                }
                Canvas canvas = mPreviewSurface.lockCanvas(null);
                canvas.drawBitmap(bm, 0, 0, paint);
                mPreviewSurface.unlockCanvasAndPost(canvas);
            }
        }, handler);
    }

    private class DeviceStateCallback extends CameraDevice.StateCallback {

        @Override public void onOpened(CameraDevice camera) {
            try {
                mCameraDevice = camera;
                Log.d(TAG, "DeviceStateCallback onOpened: " + camera.getId());

                mTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                Log.d(TAG, "onOpened: texture size : " + mPreviewSize);

                //setupImageReader();
                mRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewSurface = new Surface(mTexture);
                mRequestBuilder.addTarget(mPreviewSurface);
                //mRequestBuilder.addTarget(imageReader.getSurface());
                List<Surface> surfaces = new ArrayList<>();
                surfaces.add(mPreviewSurface);
                //surfaces.add(imageReader.getSurface());
                camera.createCaptureSession(surfaces, new SessionStateCallback(), mThreadHandler);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override public void onDisconnected(CameraDevice camera) {
            Log.d(TAG, "DeviceStateCallback onDisconnected: " + camera.getId());
        }

        @Override public void onError(CameraDevice camera, int error) {
            Log.e(TAG, "DeviceStateCallback onError: " + error);
        }
    }

    private class SessionStateCallback extends CameraCaptureSession.StateCallback {

        @Override public void onConfigured(CameraCaptureSession session) {
            Log.d(TAG, "SessionStateCallback onConfigured: ");
            mCaptureSession = session;
            try {
                if (mState == STATE_RECORDING) {
                    CaptureRequest.Builder builder =
                            mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                    builder.addTarget(mPreviewSurface);
                    builder.addTarget(inputSurface);
                    mCaptureSession.setRepeatingRequest(builder.build(), mCaptureCallback, null);
                    videoEncoder.start();
                    audioRecord.startRecording();
                    audioEncoder.start();
                } else {
                    setupRequest(mRequestBuilder);
                    mCaptureCallback = new CaptureCallback();
                    session.setRepeatingRequest(mRequestBuilder.build(), mCaptureCallback,
                            mThreadHandler);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override public void onConfigureFailed(CameraCaptureSession session) {
            Log.d(TAG, "SessionStateCallback onConfigureFailed");
        }
    }

    private class CaptureCallback extends CameraCaptureSession.CaptureCallback {
        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                CaptureResult partialResult) {
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
        }
    }

    /**
     * 生成文件名
     *
     * @param type 0: image; 1: video
     */
    private File getFile(int type) {
        String fileName =
                DateFormat.format("yyyyMMddHHmmss", System.currentTimeMillis()).toString();
        String dirType = type == 0 ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES;
        String fileType = type == 0 ? ".jpg" : ".mp4";
        return new File(Environment.getExternalStoragePublicDirectory(dirType).getAbsolutePath()
                + "/"
                + fileName
                + fileType);
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight()
                    - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices The list of sizes that the camera supports for the intended output
     * class
     * @param textureViewWidth The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
            int textureViewHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth
                        && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private int getRotation() {
        int rotation;
        if (Float.compare(mSensorX, 0) <= 0 && Float.compare(mSensorX + mSensorY, 0) < 0) {
            if (Float.compare(mSensorX, mSensorY) > 0) {
                rotation = Surface.ROTATION_180;
            } else {
                rotation = Surface.ROTATION_90;
            }
        } else if (Float.compare(mSensorX, 0) > 0 && Float.compare(mSensorX - mSensorY, 0) > 0) {
            if (Float.compare(mSensorX + mSensorY, 0) > 0) {
                rotation = Surface.ROTATION_270;
            } else {
                rotation = Surface.ROTATION_180;
            }
        } else {
            rotation = Surface.ROTATION_0;
        }
        return rotation;
    }

    private void setupRequest(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_CONTINUOUS_PICTURE);
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        try {
            for (String cameraId : cm.getCameraIdList()) {
                CameraCharacteristics characteristics = cm.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing != mFacing) {
                    continue;
                }

                StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = ((Activity) (mContext)).getWindowManager()
                        .getDefaultDisplay()
                        .getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                Log.d(TAG, "device orientation : "
                        + mSensorOrientation
                        + "; display rotation : "
                        + displayRotation);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, largest);

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class CameraSensorListener implements SensorEventListener {

        @Override public void onSensorChanged(SensorEvent event) {
            mSensorX = event.values[0];
            mSensorY = event.values[1];
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }

    public void setRecordListener(RecordListener listener) {
        this.recordListener = listener;
    }

    public interface RecordListener {
        void onVideoFrame(byte[] frame, long time);

        void onAudioFrame(byte[] frame, long time);

        void onVideoFormatChanged(MediaFormat format);

        void onAudioFormatChanged(MediaFormat format);
    }
}
