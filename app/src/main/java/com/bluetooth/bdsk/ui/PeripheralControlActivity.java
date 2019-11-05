package com.bluetooth.bdsk.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.bluetooth.bdsk.Constants;
import com.bluetooth.bdsk.R;
import com.bluetooth.bdsk.TemperatureMap;
import com.bluetooth.bdsk.bluetooth.BleAdapterService;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class PeripheralControlActivity extends Activity{
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ID = "id";
    private BleAdapterService bluetooth_le_adapter;
    private TemperatureMap temperatureMapper = new TemperatureMap();

    //class variables
    private String device_name;
    private String device_address;
    private Timer mTimer;
    private boolean sound_alarm_on_disconnect = false;
    private int alert_level;
    private boolean back_requested = false;
    private boolean share_with_server = false;
    private Switch share_switch;
    private Switch temperature_switch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peripheral_control);

        // read incoming intent data from the MainActivity
        final Intent intent = getIntent();
        device_name = intent.getStringExtra(EXTRA_NAME);
        device_address = intent.getStringExtra(EXTRA_ID);

        // show the device name
        String concatName = "Device : "+device_name+" ["+device_address+"]";
        ((TextView) this.findViewById(R.id.nameTextView)).setText(concatName);

        temperature_switch = (Switch) this.findViewById(R.id.switch2);
        temperature_switch.setEnabled(false);
        temperature_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // TODO: 05/11/2019
                if (bluetooth_le_adapter != null && bluetooth_le_adapter.isConnected()) {
                    if (!isChecked) {
                        showMsg("Switching off temperature monitoring");
                        if (bluetooth_le_adapter.setIndicationsState(
                                Constants.miji_TEMPERATURE_SERVICE,
                                Constants.miji_TEMPERATURE_SERVICE_CHAR_1,
                                false)) {
                            clearTemperature();
                        } else {
                            showMsg("Failed to inform temperature monitoring has been disabled");
                        }
                    } else {
                        showMsg("Switching on temperature monitoring");
                        if (bluetooth_le_adapter.setIndicationsState(
                                Constants.miji_TEMPERATURE_SERVICE,
                                Constants.miji_TEMPERATURE_SERVICE_CHAR_1,
                                true)) {
                        } else {
                            showMsg("Failed to inform temperature monitoring has been enabled");
                        }
                    }
                }

            }
        });

        // disable the noise button
        ((Button) PeripheralControlActivity.this.findViewById(R.id.noiseButton)).setEnabled(false);

        //Disable the temperature switch
        temperature_switch.setEnabled(false);
        temperature_switch.setChecked(false);

        // disable the LOW/MID/HIGH alert level selection buttons
        ((Button) this.findViewById(R.id.lowButton)).setEnabled(false);
        ((Button) this.findViewById(R.id.midButton)).setEnabled(false);
        ((Button) this.findViewById(R.id.highButton)).setEnabled(false);

        // hide the coloured rectangle used to show green/amber/red rssi distance
        ((LinearLayout) this.findViewById(R.id.rectangle)).setVisibility(View.INVISIBLE);

        //Share switch
        share_switch = (Switch) this.findViewById(R.id.switch1);
        share_switch.setEnabled(false);
        share_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                            // handles proximity sensor
            }
        });

        // connect to the Bluetooth adapter service
        Intent gattServiceIntent = new Intent(this, BleAdapterService.class);
        bindService(gattServiceIntent, service_connection, BIND_AUTO_CREATE);
        showMsg("READY");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        unbindService(service_connection);
        bluetooth_le_adapter = null;
    }

    private void showMsg(final String msg) {
        Log.d(Constants.TAG, msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.msgTextView)).setText(msg);
            }
        });
    }

    private final ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetooth_le_adapter = ((BleAdapterService.LocalBinder) service).getService();
            bluetooth_le_adapter.setActivityHandler(message_handler);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetooth_le_adapter = null;
        }
    };

    @SuppressLint("HandlerLeak")
    private Handler message_handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle;
            String service_uuid = "";
            String characteristic_uuid = "";
            byte[] b = null;

            //Message handling logic
            switch (msg.what){
                case BleAdapterService.MESSAGE:
                    bundle = msg.getData();
                    String text = bundle.getString(BleAdapterService.PARCEL_TEXT);
                    showMsg(text);
                    break;
                case BleAdapterService.GATT_CONNECTED:

                    //((Button) PeripheralControlActivity.this.findViewById(R.id.connectButton)).setEnabled(false);
                    //((Button) PeripheralControlActivity.this.findViewById(R.id.noiseButton)).setEnabled(true);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.connectButton)).setEnabled(false);

                    //When a Bluetooth connection has been established, we need to enable the new switch
                    temperature_switch.setEnabled(true);

                    // we're connected
                    showMsg("CONNECTED");

                    // enable the LOW/MID/HIGH alert level selection buttons
                    // ((Button) PeripheralControlActivity.this.findViewById(R.id.lowButton)).setEnabled(true);
                    // ((Button) PeripheralControlActivity.this.findViewById(R.id.midButton)).setEnabled(true);
                    // ((Button) PeripheralControlActivity.this.findViewById(R.id.highButton)).setEnabled(true);

                    bluetooth_le_adapter.discoverServices();
                    break;
                case BleAdapterService.GATT_DISCONNECT:
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.connectButton)).setEnabled(true);

                    // we're disconnected
                    showMsg("DISCONNECTED");

                    // hide the rssi distance colored rectangle
                    ((LinearLayout) PeripheralControlActivity.this
                            .findViewById(R.id.rectangle))
                            .setVisibility(View.INVISIBLE);

                    //Switch off temperature switch
                    temperature_switch.setEnabled(false);
                    temperature_switch.setChecked(false);

                    // disable the LOW/MID/HIGH alert level selection buttons
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.lowButton)).setEnabled(false);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.midButton)).setEnabled(false);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.highButton)).setEnabled(false);

                    // stop the rssi reading timer
                    stopTimer();

                    //takes into account that the user has pressed the back button and
                    // completes the process of exiting the current screen:
                    if (back_requested) {
                        PeripheralControlActivity.this.finish();
                    }
                    break;

                case BleAdapterService.GATT_SERVICES_DISCOVERED:
                    //Validate services and if ok...
                    List<BluetoothGattService> services_list = bluetooth_le_adapter.getSupportedGattServices();
                    boolean miji_device_information=false;
                    boolean miji_generic_access=false;
                    boolean miji_generic_attribute=false;
                    boolean miji_service_uuid=false;

                    //LOG SERVICES
                    for (BluetoothGattService svc : services_list) {
                        Log.d(Constants.TAG,
                                "UUID=" + svc.getUuid().toString().toUpperCase()
                                + " INSTANCE=" + svc.getInstanceId()); //Returns the instance ID for this service if a remote device offers
                                                                        //multiple services with the same UUID
                        if (svc.getUuid().toString().equalsIgnoreCase(Constants.miji_DEVICE_INFORMATION)) {
                            miji_device_information = true;
                            List<BluetoothGattCharacteristic> characteristics_list = svc.getCharacteristics();
                            //logCharacteristics(characteristics_list,"miji_DEVICE_INFORMATION");
                            continue;
                        }

                        if (svc.getUuid().toString().equalsIgnoreCase(Constants.miji_GENERIC_ACCESS)) {
                            miji_generic_access = true;
                            List<BluetoothGattCharacteristic> characteristics_list = svc.getCharacteristics();
                            //logCharacteristics(characteristics_list,"miji_GENERIC_ACCESS");
                            continue;
                        }
                        if (svc.getUuid().toString().equalsIgnoreCase(Constants.miji_GENERIC_ATTRIBUTE)) {
                            miji_generic_attribute = true;
                            List<BluetoothGattCharacteristic> characteristics_list = svc.getCharacteristics();
                            //logCharacteristics(characteristics_list,"miji_GENERIC_ATTRIBUTE");
                            continue;
                        }
                        if (svc.getUuid().toString().equalsIgnoreCase(Constants.miji_TEMPERATURE_SERVICE)) {
                            miji_service_uuid = true;
                            List<BluetoothGattCharacteristic> characteristics_list = svc.getCharacteristics();
                            logCharacteristics(characteristics_list,"miji_TEMPERATURE_SERVICE");
                            continue;
                        }
                    }
                    if (miji_device_information && miji_generic_access && miji_generic_attribute && miji_service_uuid) {
                        showMsg("Device has expected services");

                        /*
                        // enable the LOW/MID/HIGH alert level selection buttons
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.lowButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.midButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.highButton)).setEnabled(true);*/

                        /**After service discovery has completed and we’ve validated the services on the device,
                         * we’ll read the characteristics
                        bluetooth_le_adapter.readCharacteristic(
                                Constants.eLINK_LOSS_SERVICE_UUID,
                                Constants.eALERT_LEVEL_CHARACTERISTIC);*/
                    } else {
                        showMsg("Device does not have expected GATT services");
                    }
                    break;

                case BleAdapterService.NOTIFICATION_OR_INDICATION_RECEIVED:

                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);

                    if (characteristic_uuid.equalsIgnoreCase((Constants.miji_TEMPERATURE_SERVICE_CHAR_1))) {
                        Log.d(Constants.TAG, "Handling bundle temp_serv_char_1");
                        String temperatureKey = byteArrayAsHexString(b).trim().toLowerCase();
                        String temperatureValue = temperatureMapper.getValues().get(temperatureKey);
                        Log.d(Constants.TAG, "temperature key: " + temperatureKey + " is " + temperatureValue);
                        showTemperature(temperatureValue);

                    }
                    if (characteristic_uuid.equalsIgnoreCase((Constants.miji_TEMPERATURE_SERVICE_CHAR_2))) {
                        Log.d(Constants.TAG, "Handling bundle temp_serv_char_2");
                        Log.d(Constants.TAG,"bundle as byte array " + b);
                        Log.d(Constants.TAG, "bundle as String " + byteArrayAsHexString(b));
                        Log.d(Constants.TAG, "bundle length:" + b.length);
                    }
                    break;

                case BleAdapterService.GATT_CHARACTERISTIC_READ:
                    bundle = msg.getData();
                    Log.d(Constants.TAG, "Service=" + bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString().toUpperCase() + " Characteristic=" + bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase());

                    /**ATTEMPT TO READ RSSI VALUE*/
                    // show the rssi distance colored rectangle
                    ((LinearLayout) PeripheralControlActivity.this.findViewById(R.id.rectangle)).setVisibility(View.VISIBLE);
                    startReadRssiTimer();
                    break;

                case BleAdapterService.GATT_REMOTE_RSSI:
                    bundle = msg.getData();
                    int rssi = bundle.getInt(BleAdapterService.PARCEL_RSSI);
                    PeripheralControlActivity.this.updateRssi(rssi);
                    break;
            }
        }
    };

    /**Bluetooth Connection button
     * */
    public void onConnect(View view) {
        showMsg("onConnect");
        if (bluetooth_le_adapter != null) {
            if (bluetooth_le_adapter.connect(device_address)) {
                ((Button) PeripheralControlActivity.this
                        .findViewById(R.id.connectButton)).setEnabled(false);
            } else {
                showMsg("onConnect: failed to connect");
            }
        } else {
            showMsg("onConnect: bluetooth_le_adapter=null");
        }
    }

    /**If we’re connected to the peripheral device when the user presses the back button,
     * we need to respond by first disconnecting and then allowing the default response
     * to pressing the back button to be taken.
     * This function will be automatically called*/
    @Override
    public void onBackPressed() {
        Log.d(Constants.TAG, "onBackPressed");
        back_requested = true;
        if (bluetooth_le_adapter.isConnected()) {
            try {
                bluetooth_le_adapter.disconnect();
            } catch (Exception e) {
            }
        } else {
            finish();
        }
    }

    public void onLow(View view) {
    }
    public void onMid(View view) {
    }
    public void onHigh(View view) {
    }
    public void onNoise(View view) {
    }

    /**When the user clicks one of the LOW, MID or HIGH buttons and the associated characteristic
     * has been successfully updated over Bluetooth, we’ll set the colour of the text of selected
     * button and set an internal variable to the selected alert level.*/
    private void setAlertLevel(int alert_level) {
        this.alert_level = alert_level;
        ((Button) this.findViewById(R.id.lowButton)).setTextColor(Color.parseColor("#000000")); ;
        ((Button) this.findViewById(R.id.midButton)).setTextColor(Color.parseColor("#000000")); ;
        ((Button) this.findViewById(R.id.highButton)).setTextColor(Color.parseColor("#000000")); ;
        switch (alert_level) {
            case 0:
                ((Button) this.findViewById(R.id.lowButton)).setTextColor(Color.parseColor("#FF0000")); ;
                break;
            case 1:
                ((Button) this.findViewById(R.id.midButton)).setTextColor(Color.parseColor("#FF0000")); ;
                break;
            case 2:
                ((Button) this.findViewById(R.id.highButton)).setTextColor(Color.parseColor("#FF0000")); ;
                break;
        }
    }

    //LOG CHARACTERISTICS
    private void logCharacteristics(List<BluetoothGattCharacteristic> characteristics, String service_name){
        Log.d(Constants.TAG, "logCharacteristics of: " + service_name + "\n");
        for(BluetoothGattCharacteristic characteristic: characteristics){
            Log.d(Constants.TAG, "--->  " + characteristic.toString());
            Log.d(Constants.TAG, "temp char uuid: " + characteristic.getUuid());
            try{
                Log.d(Constants.TAG, "--->  " + byteArrayAsHexString(characteristic.getValue()));
            }
            catch (Exception e){
                Log.d(Constants.TAG, "--->  " + "nullPointerException caught");
            }
            List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
            //logDescriptors(descriptors,characteristic.toString());
        }
    }
    //LOG DESCRIPTORS
    private void logDescriptors(List<BluetoothGattDescriptor> descriptors, String characteristics_name){
        Log.d(Constants.TAG, "logDescriptors of: " + characteristics_name + "\n");
        if(!descriptors.isEmpty()){
            for(BluetoothGattDescriptor descriptor : descriptors){
                try{
                    Log.d(Constants.TAG, "--->  " + byteArrayAsHexString(descriptor.getValue()));
                }
                catch (Exception e){
                    Log.d(Constants.TAG, "--->  " + "nullPointerException caught");
                }
            }
        }
        else{
            //Log.d(TAG, "NO DESCRIPTORS FOUND :" + descriptors.size());
        }
    }


    private void showTemperature(final String temperature) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String temperatureString = temperature + "C°";
                ((TextView) findViewById(R.id.temperatureValue)).setText(temperatureString);
            }
        });
    }

    public static String byteArrayAsHexString(byte[] bytes) {
        if (bytes == null) {
            return "[null]";
        }
        int l = bytes.length;
        StringBuffer hex = new StringBuffer();
        for (int i = 0; i < l; i++) {
            if ((bytes[i] >= 0) & (bytes[i] < 16))
                hex.append("0");
            hex.append(Integer.toString(bytes[i] & 0xff, 16).toUpperCase());
        }
        return hex.toString();
    }

    private void startReadRssiTimer() {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                bluetooth_le_adapter.readRemoteRssi();
            }
        }, 0, 2000);
    }

    private void stopTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    //Change the color of the rectangle
    private void updateRssi(int rssi) {
        String str_RSSI = "RSSI = " + rssi;
        ((TextView) findViewById(R.id.rssiTextView)).setText(str_RSSI);
        LinearLayout layout = ((LinearLayout) PeripheralControlActivity.this.findViewById(R.id.rectangle));
        byte proximity_band = 3;
        if (rssi < -80) {
            layout.setBackgroundColor(0xFFFF0000);
        } else if (rssi < -50) {
            layout.setBackgroundColor(0xFFFF8A01);
            proximity_band = 2;
        } else {
            layout.setBackgroundColor(0xFF00FF00);
            proximity_band = 1;
        }
        layout.invalidate();
    }

    private void clearTemperature() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.temperatureValue)).setText("not available");
            }
        });
    }
}
