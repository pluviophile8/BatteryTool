package com.example.batterytool;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

/**
 * 后台电池信息刷新Service
 * 1. 启动后前台运行，防止被系统杀掉
 * 2. 定时刷新通知栏内容
 * 3. 屏幕灭了暂停刷新
 */
public class BatteryInfoService extends Service {

    private static final String CHANNEL_ID = "battery_info_channel";
    private static final int NOTIFICATION_ID = 1;

    private Handler handler = new Handler();
    private BroadcastReceiver screenReceiver;
    private boolean isScreenOn = true; // 当前屏幕状态
    private BatteryManager batteryManager;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScreenOn) {
                updateBatteryInfo();
                handler.postDelayed(this, 5000); // 每5秒刷新一次
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        registerScreenReceiver();
        startForeground(NOTIFICATION_ID, buildNotification("正在收集电池信息...")); // 启动前台通知
        handler.post(refreshRunnable); // 启动定时刷新
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // 如果Service被杀死，尝试重启
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable);
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // 本服务不支持绑定
    }

    /**
     * 更新电池信息并刷新通知
     */
    private void updateBatteryInfo() {
        if (batteryManager != null) {
            Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryStatus != null) {
                int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
                String healthString = getHealthString(health);

                int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                float temperature = temp / 10f;

                int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

                int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                float current = -currentNow / 1000f;

                String content = String.format("温度：%.1f ℃    电流：%.1f mA\n电压：%d mV  健康：%s",
                        temperature, current, voltage, healthString);
                updateNotification(content);
            }
        }
    }

    private String getHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "损坏";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "过压";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "未知故障";
            case BatteryManager.BATTERY_HEALTH_COLD: return "过冷";
            default: return "未知";
        }
    }

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    isScreenOn = false;
                    handler.removeCallbacks(refreshRunnable);
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    isScreenOn = true;
                    handler.post(refreshRunnable);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    private void updateNotification(String content) {
        Notification notification = buildNotification(content);
        startForeground(NOTIFICATION_ID, notification); // 更新前台通知
    }

    private Notification buildNotification(String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class); // 可以跳MainActivity，用户点击时手动打开界面
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
    }
}
