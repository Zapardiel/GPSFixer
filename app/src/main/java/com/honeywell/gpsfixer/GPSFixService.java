package com.honeywell.gpsfixer;

/**
 * Created by E438447 on 10/18/2017.
 */

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;


public class GPSFixService extends Service {

    //region SERVICE STATUS
    // Knows Service Lifecycle
    private static GPSFixService instance = null;

    public static boolean isInstanceCreated() {
        return instance != null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    //endregion

    private static final String TAG = "GPSFixService";
    private LocationManager mLocationManager = null;

    private static int LOCATION_INTERVAL = 45;
    private static float LOCATION_DISTANCE = 1f;
    private static boolean LOCATION_SAVE = false;

    //region " Location Listener Class"
    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;

        public LocationListener(String provider) {
            Log.e(TAG, "LocationListener " + provider);
            appendLog("Location Listener attached to: " + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.e(TAG, "onLocationChanged: " + location);
            if (LOCATION_SAVE) appendLog("Location has changed: " + location);
            mLastLocation.set(location);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.e(TAG, "onProviderDisabled: " + provider);
            appendLog("Provider has been disabled: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.e(TAG, "onProviderEnabled: " + provider);
            appendLog("Provider has been enabled: " + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.e(TAG, "onStatusChanged: " + provider);
            appendLog("Status Changed: " + provider);
        }
    }

    LocationListener[] mLocationListeners = new LocationListener[]{
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };
    //endregion


    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate");
        // Flag to know that the service is running
        instance = this;

        // Loads settings from Ini File
        try {
            Log.e(TAG, "Antes de abrir INI");
            iniFile ini = new iniFile("/sdcard/GPSFixer.ini");
            Log.e(TAG, "Despues de abrir INI");
            LOCATION_INTERVAL = ini.getInt("DEFAULT", "LOCATION_INTERVAL", 45);
            LOCATION_DISTANCE = ini.getFloat("DEFAULT", "LOCATION_DISTANCE", 1f);
            LOCATION_SAVE = ini.getBoolean("DEFAULT", "LOCATION_SAVE", false);
        } catch (IOException ex) {
            Log.e(TAG, "Ini File does not exists");
            appendLog("Ini File does not exists");
        }

        // Register Broadcast Receivers
        registerReceiver(gpsLocationReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
        registerReceiver(usbReceiver, new IntentFilter("android.hardware.usb.action.USB_STATE"));

        // Initialize Location Listeners
        initializeLocationManager();

        // Request Location Updates for GPS Provider
        RequestLocation_NetworkProvider();

        // Request Location Updates for GPS Provider
        RequestLocation_GPSProvider();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        // flag to quote service status
        instance = null;


        // unregister Broadcast Receivers
        try {
            unregisterReceiver(gpsLocationReceiver);
            unregisterReceiver(usbReceiver);
        } catch (Exception ex) {
            Log.e(TAG, "Exception Destroying Service (Unregistering Broadcasts): " + ex.getMessage());
        }

        // removing LocationListener Updates
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "Exception Destroying Service(Remove LocationListeners)" + ex.getMessage());
                }
            }
        }
    }

    //region Request Locations
    private void initializeLocationManager() {
        Log.i(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    // Request to LocationManager updates based on the Network Provider
    private void RequestLocation_NetworkProvider() {
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[1]);

        } catch (java.lang.SecurityException ex) {
            Log.e(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "network provider does not exist, " + ex.getMessage());
        }
    }

    // Request to LocationManager updates based on the GPS Provider
    private void RequestLocation_GPSProvider() {
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.e(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "gps provider does not exist " + ex.getMessage());
        }
    }

    //endregion

    //region Location Provider Change (GPS ON/OFF)
    private BroadcastReceiver gpsLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().matches("android.location.PROVIDERS_CHANGED")) {
                appendLog("Location Provider Changed");
                int locationMode = getLocationMode(context);
                if (locationMode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY) {
                    appendLog("GPS Enabled: Granted Location Updates from GPS");
                    Log.d(TAG, "Granted Location Updates from GPS");
                    // Initialize Location Listeners
                    initializeLocationManager();

                    // Request Location Updates for GPS Provider
                    RequestLocation_GPSProvider();
                } else if (locationMode == Settings.Secure.LOCATION_MODE_BATTERY_SAVING) {
                    appendLog("GPS Disabled: Removed Location Updates from GPS");
                    Log.d(TAG, "Removed Location Updates from GPS");
                    // removing LocationListener Updates
                    if (mLocationManager != null) {
                        try {
                            mLocationManager.removeUpdates(mLocationListeners[0]);
                        } catch (Exception ex) {
                            Log.i(TAG, "Exception Destroying Service(Remove LocationListeners)" + ex.getMessage());
                        }

                    }
                }
            }
        }
    };

    public int getLocationMode(Context context) {
        int locationMode = 0;

        try {
            locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            appendLog("Location Provider:" + locationMode);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return locationMode;
    }
    //endregion

    //region Log File
    public void appendLog(String info) {
        Date curDate = new Date();
        SimpleDateFormat format_date = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat format_hour = new SimpleDateFormat("hh:mm:ss");
        String DateToStr = format_hour.format(curDate);
        File logFile = new File("sdcard", "GPSFixerLog_" + format_date.format(curDate) + ".txt");

        if (!logFile.exists()) {
            // delete old files GPSFixerLog_xxx.txt
            deleteOldFiles(5);

            // create a new file GPSFixerLog_yyyyMMdd.txt
            try {
                logFile.createNewFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                Toast.makeText(GPSFixService.this, "Impossible to write a LOG File!!", Toast.LENGTH_LONG).show();
            }
        }

        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(DateToStr + ": " + info);
            buf.newLine();
            buf.flush();
            buf.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    class Pair implements Comparable {
        public long t;
        public File f;

        public Pair(File file) {
            f = file;
            t = file.lastModified();
        }

        public int compareTo(Object o) {
            long u = ((Pair) o).t;
            return t < u ? -1 : t == u ? 0 : 1;
        }
    }

    ;

    private void deleteOldFiles(int maxFiles) {
        File dir = new File("sdcard");
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches("GPSFixerLog_(.*).txt");
            }
        });

        Pair[] pairs = new Pair[files.length];
        for (int i = 0; i < files.length; i++)
            pairs[i] = new Pair(files[i]);

        // Sort them by timestamp.
        Arrays.sort(pairs);

        // Take the sorted pairs and extract only the file part, disregard the timestamp.
        if (files.length > maxFiles) {
            for (int i = 0; i < files.length - maxFiles; i++) {
                pairs[i].f.delete();
            }
        }
    }

    //endregion

    //region USB restore media scanner
    // Gets system Intent android.hardware.usb.action.USB_STATE
    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent usbIntent) {
            try {
                Date curDate = new Date();
                SimpleDateFormat format_date = new SimpleDateFormat("yyyyMMdd");

                boolean bConnected = usbIntent.getExtras().getBoolean("connected");
                if (bConnected) {
                    appendLog("USB Cable Plugged");
                    //Old Method
                    File logFile = new File("sdcard", "GPSFixerLog_" + format_date.format(curDate) + ".txt");
                    Uri uri = Uri.fromFile(logFile);
                    Intent scanFileIntent = new Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
                    sendBroadcast(scanFileIntent);
                } else {
                    appendLog("USB Cable Unplugged");
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    };
    //endregion
}