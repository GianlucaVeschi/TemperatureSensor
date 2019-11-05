package com.bluetooth.bdsk.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.bluetooth.bdsk.Constants;

import java.util.ArrayList;
import java.util.List;

public class BleScanner {
    private BluetoothLeScanner scanner = null;
    private BluetoothAdapter bluetooth_adapter = null;
    private Handler handler = new Handler();
    private ScanResultsConsumer scan_results_consumer;
    private Context context;
    private boolean scanning = false;
    private String device_name_start = "";

    /**
     * The constructor takes a Context object as an argument so that we can use it
     * to start an activity, which we need to do in the event that we find
     * that Bluetooth is currently switched off.
     */
    public BleScanner(Context context) {

        this.context = context;
        //used to acquiring access to the BluetoothAdapter in the mobile device and for checking that Bluetooth is switched on.
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        //Gives access to the BluetoothLeScanner to find other bluetooth devices in the environment, which are "advertising"
        bluetooth_adapter = bluetoothManager.getAdapter();

        // check bluetooth is available and on
        if (bluetooth_adapter == null || !bluetooth_adapter.isEnabled()) {
            Log.d(Constants.TAG, "Bluetooth is NOT switched on");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBtIntent);
        }
        Log.d(Constants.TAG, "Bluetooth is switched on");
    }

    /**
     * Allow another object to initiate Bluetooth scanning with a time limit
     * and an instance of our ScanResultsConsumer interface so that callbacks
     * can be made to its methods during scanning.
     * */
    public void startScanning(final ScanResultsConsumer scan_results_consumer, long stop_after_ms) {
        //we use a boolean to keep track of whether we’re currently scanning or not
        if (scanning) {
            Log.d(Constants.TAG, "Already scanning so ignoring startScanning request");
            return;
        }
        //this object will start the Bluetooth scanning process
        if (scanner == null) {
            scanner = bluetooth_adapter.getBluetoothLeScanner();
            Log.d(Constants.TAG, "Created BluetoothScanner object");
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (scanning) {
                    Log.d(Constants.TAG, "Stopping scanning");
                    scanner.stopScan(scan_callback);
                    setScanning(false);
                }
            }
        }, stop_after_ms);
        this.scan_results_consumer = scan_results_consumer;
        Log.d(Constants.TAG, "Scanning");
        List<ScanFilter> filters;
        filters = new ArrayList<ScanFilter>();
        ScanFilter filter = new ScanFilter.Builder().setDeviceName("Miji_Bluetooth01").build();
        filters.add(filter);
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        setScanning(true);
        scanner.startScan(filters, settings, scan_callback);
    }

    public void stopScanning() {
        setScanning(false);
        Log.d(Constants.TAG, "Stopping scanning");
        scanner.stopScan(scan_callback);
    }

    /**
     * Inner class the BluetoothLeScanner needs.
     * This method is going to be called every time the scanner collects a Bluetooth advertising
     * packet which complies with our filtering criteria
     * i.e. it includes DEVICE_NAME=”Miji_Bluetooth01”
     */
    private ScanCallback scan_callback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!scanning) {
                return;
            }
            /**
             * Pass the BluetoothDevice object that represents the remote device
             * which emitted the advertising packet, to the ScanResultsConsumer object provided
             * to this class Constructor*/
            scan_results_consumer.candidateBleDevice(
                    result.getDevice(),
                    result.getScanRecord().getBytes(),
                    result.getRssi());
        }
    };

    public boolean isScanning() {
        return scanning;
    }

    void setScanning(boolean scanning) {
        this.scanning = scanning;
        if (!scanning) {
            scan_results_consumer.scanningStopped();
        } else {
            scan_results_consumer.scanningStarted(); }
    }
}
