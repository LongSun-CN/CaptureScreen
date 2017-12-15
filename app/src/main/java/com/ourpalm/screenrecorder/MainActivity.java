package com.ourpalm.screenrecorder;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private MediaProjectionManager mMpMngr;
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static final int OVERLAY_PERMISSION_REQ_CODE = 12;
    private Intent mResultIntent = null;
    private int mResultCode = 0;
    public static final String TAG = "MainAc";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        boolean isRunning = isServiceRunning(getApplicationContext(), "com.ourpalm.screenrecorder.RecordService");
        if (!isRunning) {
            mMpMngr = (MediaProjectionManager) getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mResultIntent = ((MyApplication) getApplication()).getResultIntent();
            mResultCode = ((MyApplication) getApplication()).getResultCode();
            startActivityForResult(mMpMngr.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        }

    }

    /**
     * 打开在其他应用上层显示设置（会导致部分游戏无法获取到手机存储权限）
     */
    private void openOverlayPermissionSettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            /*if (!Settings.canDrawOverlays(this)) {
                //没有悬浮窗权限m,去开启悬浮窗权限
                try {
                    //打开 “在其他应用上层显示” 的权限弹窗
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                    Toast.makeText(this, "请打开ScreenRecorder的允许在其他应用上层显示权限，以开启屏幕录制悬浮窗", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }*/
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK) {
                Log.e(TAG, "get capture permission success!");
                mResultCode = resultCode;
                mResultIntent = data;
                ((MyApplication) getApplication()).setResultCode(resultCode);
                ((MyApplication) getApplication()).setResultIntent(data);
                ((MyApplication) getApplication()).setMpmngr(mMpMngr);
                startService(new Intent(getApplicationContext(), RecordService.class));
                Toast.makeText(this, "开始录屏", Toast.LENGTH_SHORT).show();
                moveTaskToBack(true);
                openOverlayPermissionSettings();
            }
        } else if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "权限授予失败，无法开启悬浮窗", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "权限授予成功！", Toast.LENGTH_SHORT).show();
                }
            }
            moveTaskToBack(true);
        }
    }

    /*
     * 判断服务是否启动,context上下文对象 ，className服务的name
     */
    public static boolean isServiceRunning(Context mContext, String className) {

        boolean isRunning = false;
        ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> serviceList = activityManager.getRunningServices(30);

        if (!(serviceList.size() > 0)) {
            return false;
        }

        for (int i = 0; i < serviceList.size(); i++) {
            if (serviceList.get(i).service.getClassName().contains(className) == true) {
                isRunning = true;
                break;
            }
        }
        return isRunning;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i("@@", "onDestroy方法调用，停止RecordService");
        stopService(new Intent(getApplicationContext(), RecordService.class));
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("@@", "stop方法调用");
        Button button = new Button(getApplicationContext());
        WindowManager wm = (WindowManager) getApplicationContext()
                .getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams wmParams = new WindowManager.LayoutParams();

        /**
         * 以下都是WindowManager.LayoutParams的相关属性 具体用途请参考SDK文档
         */
        wmParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT; // 这里是关键，你也可以试试2003
        wmParams.format = PixelFormat.RGBA_8888; // 设置图片格式，效果为背景透明
        /**
         * 这里的flags也很关键 代码实际是wmParams.flags |=FLAG_NOT_FOCUSABLE;
         * 40的由来是wmParams的默认属性（32）+ FLAG_NOT_FOCUSABLE（8）
         */
        wmParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wmParams.width = 1;
        wmParams.height = 1;
        wm.addView(button, wmParams); // 创建View
    }

    /**
     * activity单例模式必要重写方法
     * @param intent
     */
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);//must store the new intent unless getIntent() will return the old one
    }

}
