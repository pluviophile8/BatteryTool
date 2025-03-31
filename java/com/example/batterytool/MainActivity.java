package com.example.batterytool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "battery_info_channel";
    private NotificationManager notificationManager;
    private BatteryManager batteryManager;
    private Handler handler = new Handler();

    // 每5秒刷新一次任务
    private Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateBatteryInfo();
            handler.postDelayed(this, 3652); // 每5000毫秒（5秒）执行一次
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 如果有布局文件则调用 setContentView()（此处省略）
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        handler.post(refreshRunnable); // 启动定时任务
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "电池信息通知";
            String description = "显示当前电池温度、电流、电压和健康状态";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateBatteryInfo() {
        if (batteryManager != null) {
            // 通过 ACTION_BATTERY_CHANGED 获取电池状态
            Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryStatus != null) {
                int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
                String healthString = getHealthString(health);

                int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                float temperature = temp / 10f; // 转换为摄氏度

                int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1); // 电压，单位：毫伏
//                float voltageVolts = voltage / 1000f; // 转换为伏特

                int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                float current = -currentNow / 1000f; // 转换为毫安(mA)

                String info = String.format("温度：%.1f ℃    电流：%.1f mA\n电压：%d mV  健康：%s", temperature, current, voltage, healthString);
                updateNotification(info);
            }
        }
    }

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

    private void updateNotification(String content) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // 根据需要更换图标
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content)) // 允许多行文本
                .setOngoing(true); // 设置为持续通知

        Notification notification = builder.build();
        notificationManager.notify(1, notification);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable); // 停止定时任务，防止内存泄漏
    }
}
