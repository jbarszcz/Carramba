package com.example.carramba;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "CARRAMBA: MainActivity";

    public static final String prodURL = "https://carramba-webapp.herokuapp.com/api/obd";
    public static final String stagingURL = "https://carramba-webapp-staging.herokuapp.com/api/obd";

    public String serverURL = "https://carramba-webapp.herokuapp.com/api/obd";
    //public static final String serverURL = "http://192.168.1.136:5000";
    

    private int tripNumber;

    private JSONObject batch;
    private static final int batchSize = 10;
    private int batchCounter;

    BluetoothAdapter mBluetoothAdapter;
    OBDBluetoothConnectionService btConnectionService;
    ServerUploadService serverUploadService;
    LocationService locationService;
    LocationManager mLocationManager;

    TextView connectedText;
    TextView locationText;
    TextView speedText;
    TextView rpmText;
    TextView fuelText;
    TextView temperatureText;
    TextView logText;
    Button mainButton;
    Button serverButton;


    private boolean connected = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        setContentView(R.layout.activity_main);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        btConnectionService = new OBDBluetoothConnectionService(getApplicationContext());
        serverUploadService = new ServerUploadService(serverURL, getApplicationContext());
        locationService = new LocationService(this);

        loadUI();
        registerBroadcastReceivers();
        resetJsonBatch();


    }

    private void registerBroadcastReceivers() {
        LocalBroadcastManager.getInstance(this).registerReceiver(readDataBroadcastReceiver, new IntentFilter("dataReaded"));
        LocalBroadcastManager.getInstance(this).registerReceiver(errorBroadcastReceiver, new IntentFilter("error"));
        LocalBroadcastManager.getInstance(this).registerReceiver(infoBroadcastReceiver, new IntentFilter("info"));
        LocalBroadcastManager.getInstance(this).registerReceiver(connectedBroadcastReceiver, new IntentFilter("connectedToObd2"));
        LocalBroadcastManager.getInstance(this).registerReceiver(locationChangedReceiver, new IntentFilter("location"));
        getApplicationContext().registerReceiver(m_gpsChangeReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));

        IntentFilter disconnectedFilter = new IntentFilter();
        disconnectedFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        disconnectedFilter.addAction("disconnected");
        registerReceiver(disconnectedBroadcastReceiver, disconnectedFilter);
    }

    private void loadUI() {
        connectedText = (TextView) findViewById(R.id.connection_text_view);
        locationText = (TextView) findViewById(R.id.location_text_view);
        speedText = (TextView) findViewById(R.id.speed_text_view);
        rpmText = (TextView) findViewById(R.id.rpm_text_view);
        fuelText = (TextView) findViewById(R.id.fuel_text_view);
        temperatureText = (TextView) findViewById(R.id.temperature_text_view);


        logText = (TextView) findViewById(R.id.error_text_view);
        logText.setTextColor(Color.RED);
        logText.setMovementMethod(new ScrollingMovementMethod());

        mainButton = (Button) findViewById(R.id.btnMainButton);
        serverButton = (Button) findViewById(R.id.btnServerButton);
    }


    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: called");
        super.onDestroy();
    }

    private final BroadcastReceiver readDataBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String readedSpeed = intent.getStringExtra("Vehicle Speed");
            String readedRpm = intent.getStringExtra("Engine RPM");
            String readedTemperature = intent.getStringExtra("Engine Coolant Temperature");
            String readedFuel = calculateFuelConsumption(readedRpm);
            updateData(readedSpeed, readedRpm, readedFuel, readedTemperature);


            String jsonString;
            JSONObject emptyJson;

            try {

                emptyJson = new JSONObject();

                locationService.updateLocation();
                String longitude = locationService.getLongitude();
                String latitude = locationService.getLatitude();

                emptyJson.put("tripNumber", tripNumber);
                emptyJson.put("longitude", Double.parseDouble(longitude));
                emptyJson.put("latitude", Double.parseDouble(latitude));
                emptyJson.put("speed", Double.parseDouble(readedSpeed));
                emptyJson.put("rpm", Integer.parseInt(readedRpm));
                emptyJson.put("temperature", Double.parseDouble(readedTemperature));
                emptyJson.put("fuelConsumption", Double.parseDouble(readedFuel));
                emptyJson.put("datetime", getCurrentTimeStamp());

                batch.getJSONArray("data").put(emptyJson);


                jsonString = batch.toString();
                System.out.println(jsonString);

                batchCounter++;
                if (batchCounter == batchSize) {
                    Log.d(TAG, "onReceive: batch counter reached, uploading data to server");
                    uploadDataToServer(jsonString);
                    resetJsonBatch();
                }


            } catch (Exception e) {
                e.printStackTrace();
                sendErrorBroadcast(e.getMessage());

            }

        }
    };

    private final BroadcastReceiver errorBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String error = intent.getStringExtra("error");
            logText.setText(getCurrentTimeStamp() + ": " + error + "\n" + logText.getText());

        }
    };

    private final BroadcastReceiver infoBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String info = intent.getStringExtra("info");
            logText.setText(getCurrentTimeStamp() + ": " + info + "\n" + logText.getText());

        }
    };


    private final BroadcastReceiver locationChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String location = intent.getStringExtra("location");
            locationText.setText(location);
        }
    };

    private final BroadcastReceiver connectedBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast connectedToast = Toast.makeText(
                    getApplicationContext(),
                    "Connected!",
                    Toast.LENGTH_SHORT
            );
            connectedToast.show();
            connectedText.setText("Connected");
            connectedText.setTextColor(Color.GREEN);
            mainButton.setText("Disconnect");
            mainButton.setBackgroundColor(Color.RED);
            connected = true;
        }
    };

    private final BroadcastReceiver disconnectedBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: Received DISCONNECTED intent");
            if (connected) {
                Toast connectedToast = Toast.makeText(
                        getApplicationContext(),
                        "Disconnected",
                        Toast.LENGTH_SHORT
                );
                connectedToast.show();
                connectedText.setText("Disconnected");
                connectedText.setTextColor(Color.RED);
                connected = false;
                mainButton.setText("Choose device");
                mainButton.setBackgroundColor(Color.WHITE);
            }
        }
    };

    private final BroadcastReceiver m_gpsChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast gpsTurnedOff = Toast.makeText(
                        getApplicationContext(),
                        "GPS turned off.",
                        Toast.LENGTH_SHORT);
                gpsTurnedOff.show();


            }
            if (connected) {
                btConnectionService.stopConnection();
            }

        }
    };


    public void mainButtonClicked(View view) {

        if (!mBluetoothAdapter.isEnabled()) {
            Toast btNotEnabledToast = Toast.makeText(
                    getApplicationContext(),
                    "Bluetooth is not enabled.",
                    Toast.LENGTH_SHORT
            );
            btNotEnabledToast.show();


        } else if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast gpsNotEnabledToast = Toast.makeText(
                    getApplicationContext(),
                    "GPS is not enabled.",
                    Toast.LENGTH_SHORT
            );
            gpsNotEnabledToast.show();
        } else if (connected) {
            btConnectionService.stopConnection();
            Log.d(TAG, "mainButtonClicked: Disconnecting");

        } else {
            updateTripNumber();
            showDevicesAndConnect();
        }
    }

    private void showDevicesAndConnect() {

        ArrayList<String> deviceNameAddresses = new ArrayList<>();
        final ArrayList<String> deviceAddresses = new ArrayList<>();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {

                deviceNameAddresses.add(device.getName() + "\n");
                deviceAddresses.add(device.getAddress());

            }

        }
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_singlechoice,
                deviceNameAddresses.toArray(new String[deviceNameAddresses.size()]));

        alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                String deviceAddress = deviceAddresses.get(position);
                Log.d(TAG, "onClick: " + deviceAddress);

                btConnectionService.startConnection(deviceAddress);


            }
        });

        alertDialog.setTitle("Choose Bluetooth device");
        alertDialog.show();
    }


    public void updateData(String readedSpeed, String readedRPM, String fuelConsumption, String readedTemperature) {
        Log.i(TAG, "updateData: data updated");

        speedText.setText("Speed: " + readedSpeed + " km/h");
        rpmText.setText("RPM " + readedRPM);
        temperatureText.setText("Engine temperature: " + readedTemperature + " C");

        fuelText.setText("Fuel consumption " + fuelConsumption + " l/100km");

    }

    private void uploadDataToServer(String jsonData) {
        HttpAsyncTask asyncTask = new HttpAsyncTask(serverUploadService, jsonData, serverURL);
        try {
            asyncTask.execute();
        } catch (Exception e) {
            sendErrorBroadcast(e.getMessage());
        }
    }

    public void serverButtonClicked(View view) {
        if (serverURL.equals(prodURL)) {
            serverURL = stagingURL;
            serverButton.setText("STAGING");

        } else {
            serverURL = prodURL;
            serverButton.setText("PROD");
        }

        Log.d(TAG, "serverButtonClicked: SERVER URL" + serverURL);
    }

    private void updateTripNumber() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        int oldTripNumber = preferences.getInt("tripNumber", 1);

        tripNumber = oldTripNumber + 1;
        preferences.edit().putInt("tripNumber", tripNumber).apply();
        Log.d(TAG, "updateTripNumber: new value:" + String.valueOf(tripNumber));

    }

    private String calculateFuelConsumption(String readedRPM) {

        double constant1 = 500;
        double constant2 = 4;
        try {
            double rpm = Double.parseDouble(readedRPM);

            double fuelConsumption = rpm / constant1 + constant2;

            return String.valueOf(fuelConsumption);
        } catch (Exception e) {
            sendErrorBroadcast(e.getMessage());
            return "";
        }
    }

    private void resetJsonBatch() {
        try {
            batchCounter = 0;
            batch = new JSONObject("{\"data\": []}");
        } catch (Exception e) {
            Log.d(TAG, "resetJsonBatch: Error");
        }

    }

    private void sendErrorBroadcast(String errorMessage) {
        Intent errorIntent = new Intent("error");
        errorIntent.putExtra("error", errorMessage);
        sendBroadcast(errorIntent);

    }

    public static String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }


}








