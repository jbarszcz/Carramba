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

import com.google.android.gms.location.LocationCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "CARRAMBA: MainActivity";

    public static final String serverURL = "https://carramba-webapp-staging.herokuapp.com/api/obd";
    //public static final String serverURL = "http://192.168.1.136:5000";

    //TODO trzymac tripNumber w preferences

    private int tripNumber;

    BluetoothAdapter mBluetoothAdapter;
    OBDBluetoothConnectionService btConnectionService;
    ServerUploadService serverUploadService;
    LocationService locationService;

    TextView connectedText;
    TextView serverResponseText;
    TextView locationText;
    TextView speedText;
    TextView rpmText;
    TextView fuelText;
    TextView errorText;
    Button mainButton;

    LocationCallback mLocationCallback;


    private boolean connected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        btConnectionService = new OBDBluetoothConnectionService(getApplicationContext());
        serverUploadService = new ServerUploadService(serverURL, getApplicationContext());
        locationService = new LocationService(this);

        loadUI();
        registerBroadcastReceivers();



    }

    private void registerBroadcastReceivers() {
        LocalBroadcastManager.getInstance(this).registerReceiver(readDataBroadcastReceiver, new IntentFilter("dataReaded"));
        LocalBroadcastManager.getInstance(this).registerReceiver(errorBroadcastReceiver, new IntentFilter("error"));
        LocalBroadcastManager.getInstance(this).registerReceiver(connectedBroadcastReceiver, new IntentFilter("connectedToObd2"));
        LocalBroadcastManager.getInstance(this).registerReceiver(locationChangedReceiver, new IntentFilter("location"));
        registerReceiver(disconnectedBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    }

    private void loadUI() {
        connectedText = (TextView) findViewById(R.id.connection_text_view);
        serverResponseText = (TextView) findViewById(R.id.server_response_text_view);
        locationText = (TextView)findViewById(R.id.location_text_view);
        speedText = (TextView) findViewById(R.id.speed_text_view);
        rpmText = (TextView) findViewById(R.id.rpm_text_view);
        fuelText = (TextView) findViewById(R.id.fuel_text_view);


        errorText = (TextView) findViewById(R.id.error_text_view);
        errorText.setTextColor(Color.RED);
        errorText.setMovementMethod(new ScrollingMovementMethod());

        mainButton = (Button) findViewById(R.id.btnMainButton);
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
            String readedFuel = intent.getStringExtra("Mass Air Flow");
            updateData(readedSpeed, readedRpm, readedFuel);

            String jsonString = "";
            JSONObject emptyJson = null;
            try {
                emptyJson = Utils.createEmptyJsonObject();

                locationService.updateLocation();
                String longitude = locationService.getLongitude();
                String latitude = locationService.getLatitude();

                emptyJson.getJSONArray("data").getJSONObject(0).put("tripNumber", tripNumber);
                emptyJson.getJSONArray("data").getJSONObject(0).put("longitude",  Double.parseDouble(longitude));
                emptyJson.getJSONArray("data").getJSONObject(0).put("latitude",  Double.parseDouble(latitude));
                emptyJson.getJSONArray("data").getJSONObject(0).put("speed", readedSpeed);
                emptyJson.getJSONArray("data").getJSONObject(0).put("datetime", Utils.getCurrentTimeStamp());

                jsonString = emptyJson.toString();
                System.out.println(jsonString);


            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                //uploadDataToServer(jsonString);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    };

    private final BroadcastReceiver errorBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String error = intent.getStringExtra("error");
            //TODO zrobić żeby to były takie logi, w sensie żeby errory się dopisywały a nie nadpisywały
            // w ogóle możnaby zrobić logi takie po prostu
            errorText.append(error + "\n");
            errorText.setText(error + "\n" + errorText.getText());

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
    };


    public void mainButtonClicked(View view) {

        if (!mBluetoothAdapter.isEnabled()) {
            Toast btNotEnabledToast = Toast.makeText(
                    getApplicationContext(),
                    "Bluetooth is not enabled.",
                    Toast.LENGTH_SHORT
            );
            btNotEnabledToast.show();

        } else if (connected) {
            btConnectionService.stopConnection();

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


    //TODO stworzyc obiekt do rozmowy z obd2?
    public void updateData(String readedSpeed, String readedRPM, String readedMassAirFlow) {
        Log.i(TAG, "updateData: data updated");

        speedText.setText("Speed: " + readedSpeed);
        rpmText.setText("RPM " + readedRPM);

//        StringBuilder fuel = new StringBuilder();
//        fuel.append("Fuel consumption: ");
//        fuel.append(readedFuel);
//        fuelText.setText(fuel);
        StringBuilder fuel = new StringBuilder();
        if (!readedMassAirFlow.equals(" - ")) {


            String mafString = readedMassAirFlow;
            double MAF = Double.parseDouble(mafString.replaceAll(",", "."));

            String vsString = readedSpeed;
            double VS = Double.parseDouble(vsString.replaceAll(",", "."));

            double fuelRate = ((MAF / 14.7) * 1.3245 * 3600) * 100 / (1000 * VS);
            // Log.i(TAG, "updateData: " + fuelRate);


            fuel.append("Fuel Consumption: ");
            fuel.append(String.valueOf(fuelRate));


        } else {
            fuel.append("Fuel Consumption -");


        }
        fuelText.setText(fuel);

    }

    private void uploadDataToServer(String jsonData) {
        HttpAsyncTask asyncTask = new HttpAsyncTask(serverUploadService, jsonData);
        try {
            String serverResponse = asyncTask.execute().get();
            Log.d(TAG, "uploadDataToServer: " + serverResponse);
            serverResponseText.setText(serverResponse);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void updateTripNumber(){
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        int oldTripNumber = preferences.getInt("tripNumber", 1);

        tripNumber = oldTripNumber + 1;
        preferences.edit().putInt("tripNumber", tripNumber).apply();
        Log.d(TAG, "updateTripNumber: new value:" + String.valueOf(tripNumber));

        }
    }








