package com.ourpalm.screenrecorder;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordService extends Service {

    private static final String TAG = "RService";
    private String mVideoPath;
    private MediaProjectionManager mMpmngr;
    private MediaProjection mMpj;
    private VirtualDisplay mVirtualDisplay;
    private int windowWidth;
    private int windowHeight;
    private int screenDensity;

    private Surface mSurface;
    private MediaCodec mMediaCodec;
    private MediaMuxer mMuxer;

    private LinearLayout mCaptureLl;
    private WindowManager wm;

    private boolean isStoped = true;

    public boolean isStoped() {
        return isStoped;
    }

    private AtomicBoolean mIsQuit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private boolean mMuxerStarted = false;
    private int mVideoTrackIndex = -1;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createEnvironment();
        configureMedia();
        createFloatView();
        showCXBRunning();
    }

    /**
     * 配置视频属性
     */
    private void configureMedia() {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", windowHeight, windowWidth);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 150000);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
        try {
            mMediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mMediaCodec.createInputSurface();
        mMediaCodec.start();
    }

    /**
     * 创建录制环境
     */
    private void createEnvironment() {
        mVideoPath = Environment.getExternalStorageDirectory().getPath() + "/";
        mMpmngr = ((MyApplication) getApplication()).getMpmngr();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        // video size
        DisplayMetrics metric = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metric);
        windowWidth = metric.widthPixels;
        windowHeight = metric.heightPixels;
        if(windowWidth > windowHeight){
            windowWidth = windowWidth^windowHeight;
            windowHeight = windowWidth^windowHeight;
            windowWidth = windowWidth^windowHeight;
        }
        screenDensity = metric.densityDpi;
    }

    /**
     * 开启悬浮窗
     * 开始录制
     */
    private void createFloatView() {

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams
                (WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.RGBA_8888);
        params.x = 50;
        params.y = 50;
        params.gravity = Gravity.LEFT | Gravity.TOP;

        params.width = 10;
        params.height = 10;
        LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
        mCaptureLl = (LinearLayout) inflater.inflate(R.layout.float_record, null);
        final ImageView mCaptureIv = (ImageView) mCaptureLl.findViewById(R.id.iv_record);
        wm.addView(mCaptureLl, params);
        Log.i(TAG, "ScreenRecorder开启悬浮窗");

        recordStart();
    }

    /**
     * 停止录制方法
     */
    private void recordStop() {
        mIsQuit.set(true);
    }

    private void recordStart() {

        configureMedia();
        startVirtual();
        new Thread() {
            @Override
            public void run() {
                Log.e(TAG, "start startRecord");
                try {
                    File fileDir = new File(mVideoPath + "screenrecorder");
                    if (!fileDir.exists()) {
                        fileDir.mkdir();
                    }
                    File file = new File(mVideoPath + "screenrecorder/video.mp4");
                    if (file.exists()) {
                        file.delete();
                    }
                    mMuxer = new MediaMuxer(mVideoPath + "screenrecorder/video.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    Log.i(TAG, "begin screen recorder");
                    recordVirtualDisplay();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    release();
                }
            }
        }.start();
    }

    private void startVirtual() {
        if (mMpj != null) {
            virtualDisplay();
        } else {
            setUpMediaProjection();
            virtualDisplay();
        }
    }

    private void setUpMediaProjection() {
        int resultCode = ((MyApplication) getApplication()).getResultCode();
        Intent data = ((MyApplication) getApplication()).getResultIntent();
        mMpj = mMpmngr.getMediaProjection(resultCode, data);
    }

    private void virtualDisplay() {
        mVirtualDisplay = mMpj.createVirtualDisplay("record_screen", windowHeight, windowWidth, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mSurface, null, null);
    }

    private void recordVirtualDisplay() {
        isStoped = false;
        while (!mIsQuit.get()) {
            int index = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);
            Log.i(TAG, "dequeue output buffer index=" + index);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {//后续输出格式变化
                resetOutputFormat();
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {//请求超时
                Log.d(TAG, "retrieving buffers time out!");
                try {
                    // wait 10ms
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                }
            } else if (index >= 0) {//有效输出
                if (!mMuxerStarted) {
                    throw new IllegalStateException("MediaMuxer dose not call addTrack(format) ");
                }
                encodeToVideoTrack(index);
                mMediaCodec.releaseOutputBuffer(index, false);
            }
        }
    }

    private void encodeToVideoTrack(int index) {
        ByteBuffer encodedData = mMediaCodec.getOutputBuffer(index);

        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {//是编码需要的特定数据，不是媒体数据
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.
            // Ignore it.
            Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
            mBufferInfo.size = 0;
        }
        if (mBufferInfo.size == 0) {
            Log.d(TAG, "info.size == 0, drop it.");
            encodedData = null;
        } else {
            Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size
                    + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
                    + ", offset=" + mBufferInfo.offset);
        }
        if (encodedData != null) {
            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
            mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo);//写入
            Log.i(TAG, "sent " + mBufferInfo.size + " bytes to muxer...");
        }
    }

    private void resetOutputFormat() {
        // should happen before receiving buffers, and should only happen once
        if (mMuxerStarted) {
            throw new IllegalStateException("output format already changed!");
        }
        MediaFormat newFormat = mMediaCodec.getOutputFormat();

        Log.i(TAG, "output format changed.\n new format: " + newFormat.toString());
        mVideoTrackIndex = mMuxer.addTrack(newFormat);
        mMuxer.start();
        mMuxerStarted = true;
        Log.i(TAG, "started media muxer, videoIndex=" + mVideoTrackIndex);
    }

    private void release() {
        mIsQuit.set(false);
        mMuxerStarted = false;
        Log.i(TAG, " release() ");
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
        Log.i(TAG, "停止视频录制");
        isStoped = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recordStop();
        while (!isStoped()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Toast.makeText(RecordService.this.getApplicationContext(), "结束录屏", Toast.LENGTH_SHORT).show();

        stopForeground(true);

        if (mMpj != null) {
            mMpj.stop();
        }
        if (mCaptureLl != null) {
            wm.removeView(mCaptureLl);
        }
    }


    /**
     * 在通知栏显示
     * 前台运行
     */
    public void showCXBRunning() {

        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器 　　
        Intent nfIntent = new Intent(this, MainActivity.class);
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0)) //设置PendingIntent 　　
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_record)) // 设置下拉列表中的图标(大图标) 　　　　
                .setContentTitle("ScreenRecorder") // 设置下拉列表里的标题 　　　　
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标 　　　　
                .setContentText("正在进行屏幕录制") // 设置上下文内容 　　　　
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间 　　 　　
        Notification notification = builder.build(); // 获取构建好的Notification 　　
        startForeground(1, notification);
        Log.i(TAG, "开启ScreenRecorder前台通知");
    }
}
