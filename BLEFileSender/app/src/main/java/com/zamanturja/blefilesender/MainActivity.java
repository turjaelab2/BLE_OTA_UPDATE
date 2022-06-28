package com.zamanturja.blefilesender;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;

public class MainActivity extends AppCompatActivity {

    static final int BLE_STATE_LISTENING = 1;
    static final int BLE_STATE_CONNECTING = 2;
    static final int BLE_STATE_CONNECTED = 3;
    static final int BLE_STATE_CONNECTION_FAILED = 4;
    static final int BLE_STATE_MESSAGE_RECEIVED = 5;
    static final int BLE_STATE_DISCONNECTED = 6;

    BluetoothManager BLEManager;
    BluetoothAdapter BLEAdapter;
    BluetoothLeScanner BLEScanner;

    TextView status, filename;
    Button send;
    BluetoothDevice device_name = null;

    UUID Service_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    UUID Characteristic_UUID_RX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    UUID Characteristic_UUID_TX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    UUID Descriptor_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    BluetoothGatt blegatt = null;
    BluetoothGattCharacteristic write_characteristic = null;

    Boolean send_file = false;
    Boolean ble_connected = false;
    Client_class client_class = null;
    sendReceive sendReceive = null;

    File download_file;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.lblStatus);
        send = findViewById(R.id.btnInit);
        filename = findViewById(R.id.lblFilename);

        status.setMovementMethod(new ScrollingMovementMethod());
        buttons_enable(false);

        String path_name = Environment.getExternalStorageDirectory().toString()+ File.separator + Environment.DIRECTORY_DOWNLOADS + "/firmware.jpg";
        filename.setText(path_name);

        BLEManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BLEAdapter = BLEManager.getAdapter();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (BLEAdapter != null && BLEAdapter.isEnabled()){
            BLEScanner = BLEAdapter.getBluetoothLeScanner();
            BLEScanner.startScan(bleScanCallback);
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                download_file = new File(path_name);
                if (download_file != null){
                    try {
                        send_cmd("fn:" + "firmware.bin" + " fl:" + download_file.length());
                        Thread.sleep(1000);
                        send_cmd("start_receiving");
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        add_status("< " + e.toString() + " >");
                        e.printStackTrace();
                    }
                }
                else {
                    //
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    void send_cmd(String cmd){
        write_characteristic.setValue(cmd.getBytes());
        blegatt.writeCharacteristic(write_characteristic);
    }

    private void buttons_enable(Boolean btn_enable_disable){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (btn_enable_disable)
                {
                    send.setEnabled(true);
                    send.getBackground().setColorFilter(null);
                }
                else {
                    send.setEnabled(false);
                    send.getBackground().setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
                }
            }
        });
    }

    private ScanCallback bleScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (device_name == null && result.getDevice().getName() != null && result.getDevice().getName().equals("Aunkur")){
                add_status("Device details: " + result.getDevice().getName() + " [" + result.getDevice().getAddress() + "]");
                device_name = BLEAdapter.getRemoteDevice(result.getDevice().getAddress());
                blegatt = device_name.connectGatt(MainActivity.this, true, blegattCallback);
            }
        }
    };

    private final BluetoothGattCallback blegattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == STATE_CONNECTED) {
                add_status("Aunkur Connected");
                buttons_enable(true);
                gatt.discoverServices();
                ble_connected = true;
            } else {
                ble_connected = false;
                add_status("Aunkur Disconnected");
                buttons_enable(false);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            add_status("Get service characteristics success");
            write_characteristic = gatt.getService(Service_UUID).getCharacteristic(Characteristic_UUID_RX);
            write_characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            BluetoothGattCharacteristic mcharacteristic = gatt.getService(Service_UUID).getCharacteristic(Characteristic_UUID_TX);
            gatt.setCharacteristicNotification(mcharacteristic, true);
            BluetoothGattDescriptor descriptor = mcharacteristic.getDescriptor(Descriptor_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte readBuff[] = characteristic.getValue();
            String tmp = new String(readBuff, StandardCharsets.UTF_8);
            add_status("[" + tmp + "]<--");
            if (tmp.equals("completed")) {
                send_file = false;
                add_status("FILE SENT SUCCESSFULLY");
                send_cmd("restart");
                client_class.close();
            }
            else if (tmp.equals("send_request")) {
                send_file = true;
                client_class = new Client_class(device_name);
                client_class.start();
            }
            else if (tmp.equals("failed_FILE_IO")) {
                add_status("ESP32_failed_FILE_IO");
            }
            //else if (tmp.equals("send_request")) {
            //    runOnUiThread(new Runnable() {
            //        @Override
            //        public void run() {
            //            send.performClick();
            //        }
            //    });
            //}
            else {
                //
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            byte readBuff[] = characteristic.getValue();
            String tmp = new String(readBuff, StandardCharsets.UTF_8);
            add_status("(" + tmp + ")-->");
            if (tmp.equals("start_receiving")) {
                //send_file = true;
                //client_class = new Client_class(device_name);
                //client_class.start();
            }
        }
    };

    private void show_toast(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void add_status(String msg){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                status.append(msg + "\n");
            }
        });
    }

    private byte CRC8(byte arr[], int len){
        byte crc = 0;
        for(int i=0; i<len; i++){
            crc ^= arr[i];
        }
        return crc;
    }

    Handler handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage (@NonNull Message msg){
                switch (msg.what) {
                    case BLE_STATE_LISTENING:
                        add_status("BLE LISTENING");
                        break;
                    case BLE_STATE_CONNECTING:
                        add_status("BLE CONNECTING");
                        break;
                    case BLE_STATE_CONNECTED:
                        add_status("BLE CONNECTED");
                        if (send_file == true) {
                            try {
                                add_status("Initiating file stream");
                                FileInputStream input_data = new FileInputStream(download_file);
                                int size = (int) download_file.length() + (1024*10);
                                byte[] buffer = new byte[size]; // bytes to transfer full packet-------------------------------------------------
                                int length;
                                add_status("Sending started");
                                while ((length = input_data.read(buffer)) > 0) {
                                    sendReceive.write(buffer, 0, length);
                                }
                                add_status("Sending ended");
                            } catch (FileNotFoundException e) {
                                add_status("< " + e.toString() + " >");
                                e.printStackTrace();
                            } catch (IOException e) {
                                add_status("< " + e.toString() + " >");
                                e.printStackTrace();
                            } finally {
                                sendReceive.close();
                            }
                        } else {
                            send_cmd("stop_receiving");
                        }
                        break;
                    case BLE_STATE_DISCONNECTED:
                        add_status("BLE DISCONNECTED");
                        break;
                    case BLE_STATE_CONNECTION_FAILED:
                        add_status("BLE CONNECTION FAILED");
                        break;
                    case BLE_STATE_MESSAGE_RECEIVED:
                        byte[] readBuff = (byte[]) msg.obj;
                        String tmpMsg = new String(readBuff, 0, msg.arg1);
                        status.append(tmpMsg + "\n");
                        break;
                }
                return true;
            }
    });

    private class Client_class extends Thread{
        private BluetoothDevice device;
        private BluetoothSocket socket;
        @SuppressLint("MissingPermission")
        public Client_class(BluetoothDevice device1){
            device = device1;
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                add_status("< " + e.toString() + " >");
                e.printStackTrace();
            }
        }

        @SuppressLint("MissingPermission")
        public void run(){
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = BLE_STATE_CONNECTED;
                handler.sendMessage(message);
                sendReceive = new sendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                add_status("< " + e.toString() + " >");
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = BLE_STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }

        @SuppressLint("SetTextI18n")
        public void close(){
            if(socket.isConnected()){
                try {
                    sendReceive.close();
                    socket.close();
                    Message message = Message.obtain();
                    message.what = BLE_STATE_DISCONNECTED;
                    handler.sendMessage(message);
                } catch (IOException e) {
                    add_status("< " + e.toString() + " >");
                    e.printStackTrace();
                    add_status("BLE CANNOT DISCONNECT");
                }
            }
        }

        public boolean isConnected(){
            return socket.isConnected();
        }
    }

    private class sendReceive extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private boolean run_state = true;
        private BufferedOutputStream bos = null;

        private sendReceive(BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = bluetoothSocket.getInputStream();
                tmpOut = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                add_status("< " + e.toString() + " >");
                e.printStackTrace();
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        private byte CRC8(byte arr[], int len) {
            byte crc = 0;
            for (int i = 0; i < len; i++) {
                crc ^= arr[i];
            }
            return crc;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (run_state && bluetoothSocket.isConnected()) {
                if (!bluetoothSocket.isConnected()) {
                    add_status("BLE DISCONNECTED");
                    run_state = false;
                    if (!client_class.isConnected()) {
                        client_class.close();
                        close();
                    }
                }
                try {
                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(BLE_STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    run_state = false;
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes, int i, int length) {
            try {
                outputStream.write(bytes);
                outputStream.flush();
            } catch (IOException e) {
                add_status("< " + e.toString() + " >");
                e.printStackTrace();
            }
        }

        public void close() {
            run_state = false;
            try {
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                add_status("< " + e.toString() + " >");
                e.printStackTrace();
            }
        }
    }
}