package com.example.rohsins.stablec;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

public class WService extends Service implements MqttCallbackExtended {

    public static Boolean retain;
    public static int qos;
    public static String publishTopic;
    public static String subscribeTopic;
    public static String brokerAddress;
    public static String clientId;
    public static MemoryPersistence persistence;
    public static MqttConnectOptions connectionOption;
    public static MqttClient mqttClient;

    public static volatile boolean serviceAlive;
    public static volatile boolean serviceSwitchValue;

    NotificationCompat.Builder notificationBuilder;
    NotificationChannel notificationChannel;
    static NotificationManager notificationManager;
    public static int notificationId = 0;
    public static String notificationMessage;

    JSONObject jsonMqttMessage;

    private static PowerManager powerManager;
    private static PowerManager.WakeLock wakeLock;

    Handler notificationHandler = new Handler();

    Runnable notificationRunnable = new Runnable() {
        @Override
        public void run() {
            notificationId = (int) (System.currentTimeMillis() % 1000);
            notificationBuilder.setContentText(notificationMessage);
            notificationBuilder.setSmallIcon(R.drawable.televisions);
            notificationBuilder.setShowWhen(true);
            notificationBuilder.setWhen(System.currentTimeMillis());
            notificationManager.notify(notificationId, notificationBuilder.build());
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        serviceAlive = true;

        powerManager = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WService WakeLock");
        wakeLock.acquire();
        Toast.makeText(this, "Wake Lock Acquired", Toast.LENGTH_SHORT).show();

        retain = false;
        qos = 2;
        publishTopic = "RTSR&D/baanvak/pub/00000001";
        subscribeTopic = "RTSR&D/baanvak/sub/00000001";
        brokerAddress = "tcp://hardware.wscada.net:1883";
        clientId = "rohitTestA";

        persistence = new MemoryPersistence();
        connectionOption = new MqttConnectOptions();

        connectionOption.setUserName("rtshardware");
        connectionOption.setPassword("rtshardware".toCharArray());
        connectionOption.setAutomaticReconnect(true);
        connectionOption.setConnectionTimeout(30);
        connectionOption.setKeepAliveInterval(60);
        connectionOption.setCleanSession(false);

        try {
            mqttClient = new MqttClient(brokerAddress, clientId, persistence);
            mqttClient.connect(connectionOption);
            mqttClient.setCallback(WService.this);
            mqttClient.subscribe(subscribeTopic, qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }

        notificationBuilder = new NotificationCompat.Builder(this, "my_channel_01")
                .setSmallIcon(R.drawable.televisions)
                .setContentTitle("Mqtt Notification")
                .setLights(Color.YELLOW, 1000, 3000)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.FLAG_SHOW_LIGHTS);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel("my_channel_01", "channelF", NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setDescription("sensor channel");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.YELLOW);
            notificationChannel.enableVibration(false);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
//            globalNotificationChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});

            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceAlive = true;
//        onTaskRemoved(intent);
        Toast.makeText(this, "Starting Service", Toast.LENGTH_SHORT).show();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent sIntent = new Intent(getApplicationContext(), this.getClass());
        sIntent.setPackage(getPackageName());
        startService(sIntent);
        super.onTaskRemoved(rootIntent);
        Log.d("output", "service restarted");
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            try {
                mqttClient.subscribe(subscribeTopic, qos);
                Log.d("output", "resubscribed");
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.d("output", "connection lost");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {

        jsonMqttMessage = new JSONObject(message.toString());

        notificationMessage = jsonMqttMessage.getJSONObject("payload").getString("message");

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.putExtra("tempMessage", notificationMessage + "\n");
        PendingIntent notificationPendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(notificationPendingIntent);
        notificationHandler.post(notificationRunnable);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        serviceAlive = false;
        try {
            mqttClient.disconnect();
            mqttClient.unsubscribe(subscribeTopic);
            wakeLock.release();
            Toast.makeText(this, "Killing Service", Toast.LENGTH_SHORT).show();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
