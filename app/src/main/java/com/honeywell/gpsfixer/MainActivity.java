package com.honeywell.gpsfixer;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.os.Bundle;
import android.util.Log;
import android.location.Location;

public class MainActivity extends Activity {
    private static final String TAG = "GPSFixService";

    // Listener to get notifications when a new location is available
    public interface OnNewLocationListener {
        public abstract void onNewLocationReceived(Location location);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        runService(this);
        finish();
    }

    private void runService(Context context) {
        Intent serviceLauncher = new Intent(context, GPSFixService.class);
        if (!GPSFixService.isInstanceCreated()) {
            Log.i(TAG, "Service Running!!");
            context.startService(serviceLauncher);
            notifService();
        } else {
            Log.i(TAG, "Service Stopped!!");
            context.stopService(serviceLauncher);
            cancelNotification(context,1);
        }
    }

    protected void btnGetPoint() {
        Intent intentToFire = new Intent(ReceiverPositioningAlarm.ACTION_REFRESH_SCHEDULE_ALARM);
        intentToFire.putExtra(ReceiverPositioningAlarm.COMMAND,ReceiverPositioningAlarm.SENDER_ACT_DOCUMENT);
        sendBroadcast(intentToFire);
        OnNewLocationListener onNewLocationListener = new OnNewLocationListener() {

            @Override
            public void onNewLocationReceived(Location location) {
                // use your new location here then stop listening
                ReceiverPositioningAlarm.clearOnNewLocationListener(this);
            }
        };
        // start listening for new location
        ReceiverPositioningAlarm
                .setOnNewLocationListener(onNewLocationListener);


        //region Create Notification
    private void notifService() {
/*        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);*/

        Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, 0);

//        Bitmap bitmapIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_battery);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_gps)
//                .setLargeIcon(bitmapIcon)
                .setAutoCancel(true)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setContentTitle("GPSFixer")
                .setTicker("The service is RUNNING!!")
                .setContentText("Tap to stop it!")
                .setContentIntent(pendingIntent);

        Notification not = mBuilder.build();
        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, not);
    }

    private void cancelNotification(Context ctx, int notifyId) {
        String ns = ctx.NOTIFICATION_SERVICE;
        NotificationManager nMgr = (NotificationManager) ctx.getSystemService(ns);
        nMgr.cancel(notifyId);
    }
    //endregion
}
