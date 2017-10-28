package com.honeywell.gpsfixer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

/**
 * Created by E438447 on 2/8/2017.
 */
public class OnBootReceiver extends BroadcastReceiver {

    private Handler handler;
    private final static int REQUEST_WRITE_STORAGE = 112;
    private static final String TAG = "GPSFixService";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent serviceLauncher = new Intent(context, GPSFixService.class);
            context.startService(serviceLauncher);
            notifService(context);
            Log.e(TAG, "Service Running!!");
        }
    }

    //region Create Notification
    private void notifService(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

//        Bitmap bitmapIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_battery);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.mipmap.ic_gps)
//                .setLargeIcon(bitmapIcon)
                .setAutoCancel(true)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setContentTitle("GPSFixer")
                .setTicker("Tap to stop it!")
                .setContentText("Tap to stop it!")
                .setContentIntent(pendingIntent);

        Notification not = mBuilder.build();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, not);
    }
    //endregion
}
