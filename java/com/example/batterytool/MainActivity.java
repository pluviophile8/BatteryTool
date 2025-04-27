package com.example.batterytool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "battery_info_channel";
    private static final String ACTION_REFRESH_NOTIFICATION = "com.example.batterytool.ACTION_REFRESH_NOTIFICATION";

    private NotificationManager notificationManager;
    private BatteryManager batteryManager;
    private Handler handler = new Handler();
    private BroadcastReceiver screenReceiver;
    private BroadcastReceiver notificationClickReceiver;
    private boolean isScreenOn = true; // 标记当前屏幕状态

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScreenOn) {
                updateBatteryInfo(); // 只有屏幕亮时定时刷新
                handler.postDelayed(this, 3652); // 每3.652秒刷新一次
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);

        createNotificationChannel();
        registerScreenReceiver();
        registerNotificationClickReceiver();
        initScreenState(); // 检查当前屏幕状态
//        handler.post(refreshRunnable); // 开启定时刷新
    }

    /**
     * 创建通知通道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "电池信息通知";
            String description = "显示当前电池温度、电流、电压和健康状态，点击通知更新数据";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
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
                float temperature = temp / 10f; // 转为摄氏度

                int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

                int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                float current = -currentNow / 1000f;

                String info = String.format("温度：%.1f ℃     电流：%.1f mA\n电压：%d mV  健康：%s", temperature, current, voltage, healthString);
                updateNotification(info);
            }
        }
    }

    /**
     * 根据电池健康状态返回描述
     */
    private String getHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "损坏";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "过压";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "未知故障";
            case BatteryManager.BATTERY_HEALTH_COLD:
                return "过冷";
            default:
                return "未知";
        }
    }

    /**
     * 更新通知内容
     */
    private void updateNotification(String content) {
        // 点击通知时，发送广播，偷偷刷新
        Intent refreshIntent = new Intent(ACTION_REFRESH_NOTIFICATION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setOngoing(true)
                .setContentIntent(pendingIntent); // 点击后发广播，不跳界面

        Notification notification = builder.build();
        notificationManager.notify(1, notification);
    }

    /**
     * 注册屏幕亮灭监听器
     */
    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    isScreenOn = false;
                    handler.removeCallbacks(refreshRunnable); // 屏幕灭 - 停止刷新
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    isScreenOn = true;
                    handler.post(refreshRunnable); // 屏幕亮 - 恢复刷新
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    /**
     * 注册通知点击广播
     */
    private void registerNotificationClickReceiver() {
        notificationClickReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_REFRESH_NOTIFICATION.equals(intent.getAction())) {
                    updateBatteryInfo(); // 点击通知时偷偷刷新
                    if (isScreenOn) {
                        handler.removeCallbacks(refreshRunnable); // 先取消当前定时任务
                        handler.postDelayed(refreshRunnable, 3652); // 3.652秒后再继续刷新
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_REFRESH_NOTIFICATION);
        registerReceiver(notificationClickReceiver, filter);
    }

    /**
     * 初始化当前屏幕亮灭状态
     */
    private void initScreenState() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            isScreenOn = powerManager.isInteractive();
            if (isScreenOn) {
                handler.post(refreshRunnable);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable); // 取消定时任务
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }
        if (notificationClickReceiver != null) {
            unregisterReceiver(notificationClickReceiver);
        }
    }
}
