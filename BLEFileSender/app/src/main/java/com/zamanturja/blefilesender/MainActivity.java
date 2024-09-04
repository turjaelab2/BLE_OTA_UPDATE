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
import android.bluetooth.BluetoothProfile;
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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;

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

    BluetoothGatt bleGatt = null;
    BluetoothGattCharacteristic write_characteristic = null;

    Boolean send_file = false;
    Boolean ble_connected = false;
    Client_class client_class = null;
    sendReceive sendReceive = null;

    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long RETRY_DELAY_MS = 2000; // Delay between retries in milliseconds
    public int retryAttempts = 0;

    File download_file;

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void clearAppCache() {
        try {
            File cacheDir = getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                deleteDirectory(cacheDir);
                add_status("Cache cleared successfully.");
            }
        } catch (Exception e) {
            add_status("Error clearing cache: " + e.getMessage());
            showError(e.getMessage());
        }
    }

    private boolean deleteDirectory(File directory) {
        if (directory != null && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        if (!deleteDirectory(file)) {
                            // Log failure to delete directory
                            add_status("Failed to delete directory: " + file.getAbsolutePath());
                        }
                    } else {
                        if (!file.delete()) {
                            // Log failure to delete file
                            add_status("Failed to delete file: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
        assert directory != null;
        return directory.delete(); // Check this return value
    }

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

        String path_name = Environment.getExternalStorageDirectory().toString() + File.separator + Environment.DIRECTORY_DOWNLOADS + "/firmware.jpg";
        filename.setText(path_name);

        BLEManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BLEAdapter = BLEManager.getAdapter();

        try {
            // Clear app cache at the start
            clearAppCache();
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            showError(String.valueOf(e));
        }

        if (BLEAdapter != null && BLEAdapter.isEnabled()) {
            BLEScanner = BLEAdapter.getBluetoothLeScanner();
            BLEScanner.startScan(bleScanCallback);
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            showError(String.valueOf(e));
        }

        send.setOnClickListener(v -> {
            download_file = new File(path_name);
            try {
                send_cmd("fn:" + "firmware.bin" + " fl:" + download_file.length());
                Thread.sleep(1000);
                send_cmd("start_receiving");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                add_status("< " + e + " >");
                showError(String.valueOf(e));
            }
        });
    }

    @SuppressLint("MissingPermission")
    void send_cmd(String cmd) {
        write_characteristic.setValue(cmd.getBytes());
        bleGatt.writeCharacteristic(write_characteristic);
    }

    private void buttons_enable(Boolean btn_enable_disable) {
        runOnUiThread(() -> {
            if (btn_enable_disable) {
                send.setEnabled(true);
                send.getBackground().setColorFilter(null);
            } else {
                send.setEnabled(false);
                send.getBackground().setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
            }
        });
    }

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (device_name == null && result.getDevice().getName() != null && result.getDevice().getName().equals("Aunkur")) {
                add_status("Device details: " + result.getDevice().getName() + " [" + result.getDevice().getAddress() + "]");
                device_name = BLEAdapter.getRemoteDevice(result.getDevice().getAddress());
                //bleGatt = device_name.connectGatt(MainActivity.this, true, bleGattCallback);
                connectToDevice(); // Start connection attempt
            }
        }
    };

    private void restartApp() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish(); // Close the current activity
        System.exit(0); // Exit the app completely (optional, depending on your needs)
    }


    @SuppressLint("MissingPermission")
    private void showBluetoothOffPrompt() {
        new AlertDialog.Builder(this)
                .setTitle("Oops :D")
                .setMessage("It looks like there is an issue with Bluetooth. Please turn off Bluetooth and turn it back on, then reopen the app to resolve the problem.")
                .setPositiveButton("OK", (dialog, which) -> {
                    // Optionally, you can redirect the user to Bluetooth settings
                    Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    startActivity(intent);
                    finishAffinity();
                    System.exit(0); // Optionally force close the app
                })
                .setNegativeButton("Cancel", (dialog, which) -> restartApp())
                .show();
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        if (device_name != null) {
            try {
                add_status("Attempting to connect...[" + retryAttempts + "]");
                bleGatt = device_name.connectGatt(MainActivity.this, true, bleGattCallback);
                if (retryAttempts > 0) {
                    if (retryAttempts == MAX_RETRY_ATTEMPTS) {
                        bleGatt.close();
                        showBluetoothOffPrompt();
                    }
                    //new Handler().postDelayed(() -> {
                    //    bleGatt = device_name.connectGatt(MainActivity.this, true, bleGattCallback);
                    //    add_status("Bluetooth is being enabled...");
                    //}, 1000);
                }
            } catch (Exception e) {
                add_status("< " + e + " >");
                // Retry if there's an exception
                retryAttempts++;
                handler.postDelayed(this::connectToDevice, RETRY_DELAY_MS);
            }
        }
    }

    private final BluetoothGattCallback bleGattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                add_status("Aunkur Connected");
                buttons_enable(true);
                gatt.discoverServices();
                ble_connected = true;
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                add_status("Aunkur Disconnected");
                buttons_enable(false);
                ble_connected = false;

                // Retry connection if needed
                if (retryAttempts < MAX_RETRY_ATTEMPTS) {
                    retryAttempts++;
                    handler.postDelayed(() -> connectToDevice(), RETRY_DELAY_MS);
                } else {
                    add_status("Connection re-attempt failed !");
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            add_status("Get service characteristics success");
            write_characteristic = gatt.getService(Service_UUID).getCharacteristic(Characteristic_UUID_RX);
            write_characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            BluetoothGattCharacteristic mCharacteristic = gatt.getService(Service_UUID).getCharacteristic(Characteristic_UUID_TX);
            gatt.setCharacteristicNotification(mCharacteristic, true);
            BluetoothGattDescriptor descriptor = mCharacteristic.getDescriptor(Descriptor_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] readBuff = characteristic.getValue();
            String tmp = new String(readBuff, StandardCharsets.UTF_8);
            add_status("[" + tmp + "]<--");
            switch (tmp) {
                case "completed":
                    send_file = false;
                    add_status("FILE SENT SUCCESSFULLY");
                    send_cmd("restart");
                    client_class.close();
                    break;
                case "send_request":
                    send_file = true;
                    client_class = new Client_class(device_name);
                    client_class.start();
                    break;
                case "failed_FILE_IO":
                    add_status("ESP32_failed_FILE_IO");
                    break;
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            byte[] readBuff = characteristic.getValue();
            String tmp = new String(readBuff, StandardCharsets.UTF_8);
            add_status("(" + tmp + ")-->");
        }
    };

    private void add_status(String msg) {
        runOnUiThread(() -> status.append(msg + "\n"));
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case BLE_STATE_LISTENING:
                    add_status("BLE LISTENING");
                    break;
                case BLE_STATE_CONNECTING:
                    add_status("BLE CONNECTING");
                    break;
                case BLE_STATE_CONNECTED:
                    add_status("BLE CONNECTED");
                    new Thread(() -> {
                        if (send_file) {
                            try {
                                add_status("Initiating file stream");

                                try (FileInputStream inputData = new FileInputStream(download_file)) {
                                    long fileSize = download_file.length();
                                    long bytesSent = 0;
                                    int chunkSize = 512; // Define the size of the chunks to send
                                    byte[] buffer = new byte[chunkSize];

                                    add_status("Sending started");

                                    int bytesRead;
                                    // Variable to keep track of the last progress value
                                    int lastProgress = -1;
                                    while ((bytesRead = inputData.read(buffer)) > 0) {
                                        synchronized (this) {
                                            wait(50); // Adjust wait time as needed
                                        }
                                        sendReceive.write(buffer);
                                        bytesSent += bytesRead;

                                        // Only update the UI if the progress has changed
                                        int progress = (int) ((bytesSent * 100) / fileSize);
                                        if (progress != lastProgress) {
                                            lastProgress = progress;  // Update the last progress value
                                            runOnUiThread(() -> add_status("[" + progress + "%]-->"));
                                        }
                                    }
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                add_status("Sending ended");
                            } catch (IOException e) {
                                add_status("< " + e + " >");
                                showError(e.getMessage());
                            } finally {
                                sendReceive.close();
                            }
                        } else {
                            send_cmd("stop_receiving");
                        }
                    }).start();
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

    private class Client_class extends Thread {
        private BluetoothSocket socket;

        @SuppressLint("MissingPermission")
        public Client_class(BluetoothDevice device) {
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                add_status("< " + e + " >");
            }
        }

        @SuppressLint("MissingPermission")
        public void run() {
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = BLE_STATE_CONNECTED;
                handler.sendMessage(message);
                sendReceive = new sendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                add_status("< " + e + " >");
                showError(String.valueOf(e));
                Message message = Message.obtain();
                message.what = BLE_STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }

        @SuppressLint("SetTextI18n")
        public void close() {
            if (socket.isConnected()) {
                try {
                    sendReceive.close();
                    socket.close();
                    Message message = Message.obtain();
                    message.what = BLE_STATE_DISCONNECTED;
                    handler.sendMessage(message);
                } catch (IOException e) {
                    add_status("< " + e + " >");
                    showError(String.valueOf(e));
                    add_status("BLE CANNOT DISCONNECT");
                }
            }
        }

        public boolean isConnected() {
            return socket.isConnected();
        }
    }

    private class sendReceive extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private boolean run_state = true;

        private sendReceive(BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = bluetoothSocket.getInputStream();
                tmpOut = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                add_status("< " + e + " >");
                showError(String.valueOf(e));
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
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
                    showError(String.valueOf(e));
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
                outputStream.flush();
            } catch (IOException e) {
                add_status("< " + e + " >");
                showError(String.valueOf(e));
            }
        }

        public void close() {
            run_state = false;
            try {
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                add_status("< " + e + " >");
                showError(String.valueOf(e));
            }
        }
    }
}