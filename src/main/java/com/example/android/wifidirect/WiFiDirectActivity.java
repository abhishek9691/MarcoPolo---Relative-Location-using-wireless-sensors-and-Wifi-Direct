/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wifidirect;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class WiFiDirectActivity extends Activity implements ChannelListener, DeviceActionListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, SensorEventListener {
    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    /**
     * Represents a geographical location.
     */
    protected Location mLastLocation;

    protected TextView mLatitudeText;
    protected TextView mLongitudeText;
    protected TextView zLocationText;
    protected double latitudeVal;
    protected double longitudeVal;
    protected double zLocation;

    /* for the wifi direct portion  */
    protected static final String TAG = "wifidirectdemo";
    private WifiP2pManager manager;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;

    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;

    /* to collect data from the sensors */
    private SensorManager sensorManager;
    private Sensor sensor;
    private long lastUpdate = 0;
    private long lastCompute = 0;
    private float last_x, last_y, last_z;
    private static final int SHAKE_THRESHOLD = 600;
    private Vector<Float> accelDataX;
    private Vector<Float> accelDataY;
    private Vector<Float> accelDataZ;
    private double lastAvgX=0, lastAvgY=0, lastAvgZ=0;
    private double lastStepX=0, lastStepY=0,lastStepZ=0;
    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mLatitudeText = (TextView) findViewById((R.id.latitude_text));
        mLongitudeText = (TextView) findViewById((R.id.longitude_text));
        zLocationText = (TextView) findViewById((R.id.z_text));
        latitudeVal = 0;
        longitudeVal = 0;
        zLocation = 0;
        accelDataX = new Vector<Float>();
        accelDataY = new Vector<Float>();
        accelDataZ = new Vector<Float>();

        // add necessary intent values to be matched.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        //setup the sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        buildGoogleApiClient();
    }

    /** register the BroadcastReceiver with the intent values to be matched */
    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        sensorManager.unregisterListener(this);
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.atn_direct_enable:
                if (manager != null && channel != null) {

                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // WiFiDeviceBroadcastReceiver instead.

                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                } else {
                    Log.e(TAG, "channel or manager is null");
                }
                return true;

            case R.id.atn_direct_discover:
                if (!isWifiP2pEnabled) {
                    Toast.makeText(WiFiDirectActivity.this, R.string.p2p_off_warning,
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                buildGoogleApiClient();
                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                        .findFragmentById(R.id.frag_list);
                fragment.onInitiateDiscovery();
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Initiated",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Failed : " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void showDetails(WifiP2pDevice device) {
        DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.showDetails(device);

    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void connect(WifiP2pConfig config) {
        manager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(WiFiDirectActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
        final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.resetViews();
        manager.removeGroup(channel, new ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
            }

            @Override
            public void onSuccess() {
                fragment.getView().setVisibility(View.GONE);
            }

        });
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            resetData();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (manager != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_list);
            if (fragment.getDevice() == null
                    || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {

                manager.cancelConnect(channel, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectActivity.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectActivity.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

    }

    @Override
    public void onConnected(Bundle bundle) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (mLastLocation != null) {
            latitudeVal = mLastLocation.getLatitude();
            longitudeVal = mLastLocation.getLongitude();
            zLocation = 0.0;

            mLatitudeText.setText(String.valueOf(latitudeVal));
            mLongitudeText.setText(String.valueOf(longitudeVal));
            zLocationText.setText(String.valueOf(zLocation));

            String locationUpdate = "Longitude:"+longitudeVal+",Latitude:"+latitudeVal+",Z:"+zLocation+
                    ";\n";
            writeData(locationUpdate);
        } else {
            Toast.makeText(this, "No location detected", Toast.LENGTH_LONG).show();
            mLatitudeText.setText("Please turn GPS on");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }
    /**
     * Builds a GoogleApiClient. Uses the addApi() method to request the LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    /*
     Keeps track of the sensor. Whenever the sensor changes, this method is called
     */
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;
        if(mySensor.getType() == Sensor.TYPE_ACCELEROMETER){
            float x = sensorEvent.values[0];
            float y = sensorEvent.values[1];
            float z = sensorEvent.values[2];
            long currTime = System.currentTimeMillis();
            if((currTime - lastCompute) > 12000){
                //compute the transform and save the data first
                lastCompute = currTime;
                computeTransform();
                //clear data so it does not affect future result checks
                accelDataX.clear();
                accelDataY.clear();
                accelDataZ.clear();
            }
            if((currTime - lastUpdate) > 100){
                long diffTime = (currTime - lastUpdate);
                lastUpdate = currTime;
                //float speed = Math.abs(x + y + z - last_x - last_y - last_z)/ diffTime * 10000;
                //if (speed > SHAKE_THRESHOLD) {
                    //here we know that there has been a chance in the sensor, so lets do the update
                    //TextView sp = ((TextView) findViewById(R.id.speed));
                    //sp.setText(String.valueOf(speed));
                //}
                float x_corrected = (float) (x - 9.81);
                float y_corrected = (float) (y - 9.81);
                float z_corrected = (float) (z - 9.81);

                ((TextView) findViewById(R.id.x_coord)).setText("X coordinate: "+String.valueOf(x_corrected));
                ((TextView) findViewById(R.id.y_coord)).setText("Y coordinate: "+String.valueOf(y_corrected));
                ((TextView) findViewById(R.id.z_coord)).setText("Z coordinate: "+String.valueOf(z_corrected));

                last_x = x_corrected;
                last_y = y_corrected;
                last_z = z_corrected;
                Point3D pt = new Point3D(x_corrected, y_corrected, z_corrected);

                accelDataX.add(x_corrected);
                accelDataY.add(y_corrected);
                accelDataZ.add(z_corrected);
                String accel = "x:"+x_corrected+",y:"+y_corrected+",z:"+z_corrected+";\n";
                writeData(accel);
                //Log.d(TAG, pt.toString());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void computeTransform(){
        Vector<Float> copyX = new Vector<Float>(accelDataX);
        Vector<Float> copyY = new Vector<Float>(accelDataY);
        Vector<Float> copyZ = new Vector<Float>(accelDataZ);
        int size = copyX.size();
        Float[] xs = new Float[size];
        Float[] ys = new Float[size];
        Float[] zs = new Float[size];
        double [] inimg = new double[size];
        for(int i = 0; i < size; i++){
            inimg[i] = 1;
        }
        double [] outimg = new double[size];
        copyX.toArray(xs);
        copyY.toArray(ys);
        copyZ.toArray(zs);
        double [] xin = new double[size];
        double [] yin = new double[size];
        double [] zin = new double[size];
        for(int i = 0; i < size; i++){
            xin[i] = xs[i].doubleValue();
            yin[i] = ys[i].doubleValue();
            zin[i] = zs[i].doubleValue();
        }
        double[] xresult= new double[size];
        double[] yresult= new double[size];
        double[] zresult= new double[size];
        computeDft(xin, inimg, xresult, outimg);
        computeDft(yin, inimg, yresult, outimg);
        computeDft(zin, inimg, zresult, outimg);
        double avgX = 0;
        double avgY = 0;
        double avgZ = 0;
        for(int i = 0; i < size; i++){
            //Log.d(TAG, "DFT result of X @ i = "+ i+ " : " + xresult[i]);
            //Log.d(TAG, "DFT result of Y @ i = "+ i+" : "+yresult[i]);
            //Log.d(TAG, "DFT result of Z @ i = "+ i+" : "+zresult[i]);
            avgX += xresult[i];
            avgY += yresult[i];
            avgZ += zresult[i];
        }
        //avgX = avgX / ((float)size) / ((float) 3);
        //avgY = avgY / ((float)size) / ((float) 3);
        //avgZ = avgZ / ((float)size) / ((float) 3);

        //see if we can study the affects of the averages, see if they change over time
        double avgXDiff = Math.abs(avgX - lastAvgX);
        double avgYDiff = Math.abs(avgY - lastAvgY);
        double avgZDiff = Math.abs(avgZ - lastAvgZ);
        //can only distinguish between walking vs stairs, not up or down
        //did not account for runningg
        if(avgZDiff > 100 && avgXDiff > 150){
            //this difference is between this "stairs" and the last one
            //a significant difference indicates that we went down
            double stepZDiff = Math.abs(avgZ - lastStepZ);
            double stepXDiff = Math.abs(avgX - lastStepX);
            if(Math.abs(avgZ) > Math.abs(lastStepZ) && Math.abs(avgX) > Math.abs(lastStepX)
                    && stepZDiff > 30 && stepXDiff > 20){
                zLocation -= 1.0;
            } else {
                zLocation += 1.0;
            }
            lastStepZ = avgZ;
            lastStepY = avgY;
            lastStepX = avgX;
        }

        zLocationText.setText(String.valueOf(zLocation));

        lastAvgX = avgX;
        lastAvgY = avgY;
        lastAvgZ = avgZ;

        Log.d(TAG, "DFT avg result of X : " + avgX);
        Log.d(TAG, "DFT avg result of Y : " + avgY);
        Log.d(TAG, "DFT avg result of Z : " + avgZ);

        String sums = "SumX:"+avgX+","+"SumY:"+avgY+","+"SumZ"+avgZ+";\n";
        writeData(sums);
        printImportData();
    }

    public void printImportData(){
        final File f = new File(Environment.getExternalStorageDirectory() + "/"
                + getPackageName() + "/data_import.txt");
        try {
            if(!f.exists())
                return;
            BufferedReader br = new BufferedReader(new FileReader(f));
            List<String> lines = new LinkedList<String>();
            for(String line; (line = br.readLine()) != null; ){
                //Log.d(TAG, line);
                lines.add(line);
            }
            for(int i = lines.size()-1; i > 0; i--){
                String line = lines.get(i);
                if(line.contains("Longitude")){
                    String lineCopy = line.substring(0, line.indexOf(";"));
                    String[] split = lineCopy.split(",");
                    //Log.d(TAG, split[0].substring(split[0].lastIndexOf(":")+1));
                    double longitude = Double.valueOf(split[0].substring(split[0].lastIndexOf(":")+1));
                    double latitude = Double.valueOf(split[1].substring(split[1].lastIndexOf(":")+1));
                    double zlocation = Double.valueOf(split[2].substring(split[2].lastIndexOf(":")+1));
                    double longDiff = longitude - longitudeVal;
                    double latDiff= latitude- latitudeVal;
                    double zDiff= zlocation - this.zLocation;
                    String relative = "Latitude: "+String.valueOf(latDiff)+
                            ", Longitude: "+String.valueOf(longDiff)+
                            ", Z location: "+String.valueOf(zDiff);

                    ((TextView) findViewById(R.id.relativelocation)).setText(relative);
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeData(String data){
        final File f = new File(Environment.getExternalStorageDirectory() + "/"
                + getPackageName() + "/data.txt");
        File dirs = new File(f.getParent());
        if (!dirs.exists())
            dirs.mkdirs();
        try {
            if(!f.exists())
                f.createNewFile();
            FileOutputStream fos = new FileOutputStream(f, true);
            fos.write(data.getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void computeTransform(View view){
        computeTransform();
    }
    /*
	 * Computes the discrete Fourier transform (DFT) of the given vector.
	 * All the array arguments must have the same length.
	 */
    public static void computeDft(double[] inreal, double[] inimag, double[] outreal, double[] outimag) {
        int n = inreal.length;
        for (int k = 0; k < n; k++) {  // For each output element
            double sumreal = 0;
            double sumimag = 0;
            for (int t = 0; t < n; t++) {  // For each input element
                double angle = 2 * Math.PI * t * k / n;
                sumreal +=  inreal[t] * Math.cos(angle) + inimag[t] * Math.sin(angle);
                sumimag += -inreal[t] * Math.sin(angle) + inimag[t] * Math.cos(angle);
            }
            outreal[k] = sumreal;
            outimag[k] = sumimag;
        }
    }
}
