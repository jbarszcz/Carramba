package com.example.carramba;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.github.pires.obd.commands.*;
import com.github.pires.obd.commands.engine.MassAirFlowCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.fuel.ConsumptionRateCommand;
import com.github.pires.obd.commands.protocol.*;
import com.github.pires.obd.commands.temperature.EngineCoolantTemperatureCommand;
import com.github.pires.obd.enums.ObdProtocols;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class OBDBluetoothConnectionService {
    private static final String TAG = "CARRABMA: BTService";
    public static final String appName = "Carramba";
    private static final UUID MY_UUID_INSECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final BluetoothAdapter mBluetoothAdapter;
    private ArrayList<ObdCommand> commandsList;
    Context mContext;

    private ConnectThread mConnectThread;
    private BluetoothDevice mBtDevice;

    private InputStream btInputStream;
    private OutputStream btOutputStream;

    private AtomicBoolean gatherData = new AtomicBoolean();


    public OBDBluetoothConnectionService(Context mContext) {
        this.mContext = mContext;
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //this.mBtDevice = mBluetoothAdapter.getRemoteDevice(remoteDeviceAddress);
        // this.commandsList = new ArrayList<>();
    }


    public void startConnection(String remoteDeviceAddress) {
        mBtDevice = mBluetoothAdapter.getRemoteDevice(remoteDeviceAddress);
        mConnectThread = new ConnectThread(mBtDevice, MY_UUID_INSECURE);
        mConnectThread.start();

    }

    public void stopConnection() {

        mConnectThread.cancel();
    }

    private class ConnectThread extends Thread {
        BluetoothSocket socket;

        private ConnectThread(BluetoothDevice device, UUID uuid) {
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                e.printStackTrace();
                sendErrorBroadcast(e.getMessage());
            }
        }

        public void run() {
            try {
                gatherData.set(true);
                socket.connect();
                btInputStream = socket.getInputStream();
                btOutputStream = socket.getOutputStream();

                Intent connectedIntent = new Intent("connectedToObd2");
                //moze jednak lepiej wykorzyscac ACL_CONNECTED intent?
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(connectedIntent);
                startOBDCommunication(socket);
                lanuchDataGatheringLoop();
                Log.d(TAG, "run: WYJSCIE Z PETLI!");
                socket.close();

            } catch (IOException e) {
                e.printStackTrace();
                sendErrorBroadcast(e.getMessage());
                Log.d(TAG, "run: Didn't connect.");
            }
        }

        private void cancel() {
            try {
                gatherData.set(false);
            } catch (Exception e) {
                sendErrorBroadcast(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void startOBDCommunication(BluetoothSocket socket) {
        // TODO
        // dodatkowo obsłużyć wyjątek, eby nie kładło całego programu
        // może próbować np 10 razy odpalić komende jesli sie nie udalo
        // dodać przycisk do konczenia polaczenia z obd2 i najlepiej by bylo wyswietlac gdzies odpowiedzi (/zrobic jakies logowanie w samej apce, zeby tylko dodawalo odpowiedzi)


        //init bus

        executeInitBus(socket);

        SpeedCommand speedCommand = new SpeedCommand();
        RPMCommand rpmCommand = new RPMCommand();
        ConsumptionRateCommand consumptionRateCommmand = new ConsumptionRateCommand();
        EngineCoolantTemperatureCommand engineCoolantTemperatureCommand = new EngineCoolantTemperatureCommand();

        commandsList = new ArrayList<>();
        commandsList.add(speedCommand);
        commandsList.add(rpmCommand);
        commandsList.add(engineCoolantTemperatureCommand);


    }

    private void executeInitBus(BluetoothSocket socket) {
        try {
            new ObdRawCommand("AT D").run(socket.getInputStream(), socket.getOutputStream()); //AT D - set all to defaults
            new ObdResetCommand().run(socket.getInputStream(), socket.getOutputStream()); //AT Z
            new EchoOffCommand().run(socket.getInputStream(), socket.getOutputStream()); //AT E0
            new LineFeedOffCommand().run(socket.getInputStream(), socket.getOutputStream()); //AT L0
            new SpacesOffCommand().run(socket.getInputStream(), socket.getOutputStream()); // AT S0
            new HeadersOffCommand().run(socket.getInputStream(), socket.getOutputStream()); //AT H0
            new SelectProtocolCommand(ObdProtocols.AUTO).run(socket.getInputStream(), socket.getOutputStream()); //AT SP 0
            Log.d(TAG, "Intial commands executed.");

        } catch (Exception e) {
            e.printStackTrace();
            sendErrorBroadcast(e.getMessage());
            Log.e(TAG, "Couldnt run commands: " + e.getMessage());
        }
    }

    private void lanuchDataGatheringLoop() {
        while (gatherData.get()) {
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                Log.e(TAG, "lanuchDataGatheringLoop: ", e);
            }

            Intent dataReadedIntent = new Intent("dataReaded");
            for (ObdCommand command : commandsList) {
                try {
                    command.run(btInputStream, btOutputStream);
                    String commandResult = command.getCalculatedResult();
                    dataReadedIntent.putExtra(command.getName(), commandResult);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "startOBDCommunication: Wyjatek w FOR" + e.getMessage());
                    dataReadedIntent.putExtra(command.getName(), " - ");
                    sendErrorBroadcast(e.getMessage());
                }

            }

            //poszaleć z multiple commands?

            LocalBroadcastManager.getInstance(mContext).sendBroadcast(dataReadedIntent);

        }


    }

    private void sendErrorBroadcast(String errorMessage) {
        Intent errorIntent = new Intent("error");
        errorIntent.putExtra("error", errorMessage);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(errorIntent);

    }


}
