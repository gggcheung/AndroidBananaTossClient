package com.example.jacky.bananatossclient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.UUID;

/**
 * Handled all BLE related configurations and events
 */
public class BleManager {
    private static final String TAG = BleManager.class.getSimpleName();

    // BlueFruit BLE events
    public static final int BLE_EVENT_RESET = 1;
    public static final int BLE_EVENT_COUNT_UP = 2;

    // Internal BLE states
    private static final int BLE_STATE_CONNECTED = 1;
    private static final int BLE_STATE_SERVICE_DISCOVERED = 2;
    private static final int BLE_STATE_EVENT_AVAILABLE = 3;

    private static final String ADAFRUIT_DEVICE_NAME = "Adafruit Bluefruit LE";

    // BlueFruit Nordic UART service and characteristics
    private static final String UUID_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String UUID_RX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    private static String CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private boolean mScanning;
    private boolean mConnected;
    private BluetoothGatt mGatt;
    private BleGattCallback mGattCallback = new BleGattCallback();
    private BleManagerListener mListener;
    private Context mContext;

    // Class is singleton
    private static BleManager mInstance;

    // This will be run on the main thread.  It is probably OK because all GATT operations are
    // non-blocking.
    Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case BLE_STATE_CONNECTED: {
                    // BLE device connected.  Start service discovery.
                    stopScan();
                    mGatt.discoverServices();
                    mConnected = true;
                    Log.d(TAG, "Bluefruit LE connected!");
                    break;
                }
                case BLE_STATE_SERVICE_DISCOVERED: {
                    // BLE services discovered.  Enable UART RX notification.
                    final BluetoothGattService uartService =
                            mGatt.getService(UUID.fromString(UUID_SERVICE));
                    if (uartService == null) {
                        break;
                    }
                    final BluetoothGattCharacteristic rxCharacteristic =
                            uartService.getCharacteristic(UUID.fromString(UUID_RX));
                    if (rxCharacteristic == null) {
                        break;
                    }
                    final UUID clientCharacteristicConfiguration =
                            UUID.fromString(CHARACTERISTIC_CONFIG);
                    final BluetoothGattDescriptor config = rxCharacteristic.getDescriptor(
                            clientCharacteristicConfiguration);
                    config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mGatt.setCharacteristicNotification(rxCharacteristic, true);
                    mGatt.writeDescriptor(config);
                    Log.d(TAG, "UART RX notification enabled");
                    break;
                }
                case BLE_STATE_EVENT_AVAILABLE: {
                    // BLE notification (event) recevied.
                    if (mListener != null) {
                        mListener.onEvent(msg.arg1);
                        Log.d(TAG, "Event received: " + msg.arg1);
                    }
                    break;
                }
                default:
                    Log.d(TAG, "unknown message: " + msg);
                    break;
            }
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    if (device == null || device.getName() == null) {
                        return;
                    }

                    if  (device.getName().equals(ADAFRUIT_DEVICE_NAME)) {
                        mGatt = device.connectGatt(mContext, false, mGattCallback);
                        Log.d(TAG, "Bluefruit LE found");
                    }
                }
            };

    public static BleManager getInstance(Context context) {
        if(mInstance == null)
        {
            mInstance = new BleManager(context);
        }
        return mInstance;
    }

    private BleManager(Context context) {
        mScanning = false;
        mConnected = false;
        mContext = context;
    }

    public void setListener(BleManagerListener listener) {
        if (mListener != null) {
            Log.w(TAG, "listener already set");
            return;
        }
        mListener = listener;
    }

    public void startScan() {
        if (mScanning) {
            Log.d(TAG, "already scanning");
            return;
        }

        if (mConnected) {
            Log.w(TAG, "why scan if already connected?");
            return;
        }

        mBluetoothAdapter.startLeScan(mLeScanCallback);
        mScanning = true;
        Log.d(TAG, "start scanning");
    }

    public void stopScan() {
        if(mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
            Log.d(TAG, "stop scanning");
        }
    }

    class BleGattCallback extends BluetoothGattCallback {
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            final byte[] bytes =characteristic.getValue();
            final int event = (bytes.length > 0)? (bytes[0] - '0') : 0;

            Message connectedMessage = mHandler.obtainMessage(
                    BLE_STATE_EVENT_AVAILABLE, event, 0);
            connectedMessage.sendToTarget();
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChanged status: " + status + " state: " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Message connectedMessage = mHandler.obtainMessage(BLE_STATE_CONNECTED);
                connectedMessage.sendToTarget();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered");
            if (status == 0) {
                Message connectedMessage = mHandler.obtainMessage(BLE_STATE_SERVICE_DISCOVERED);
                connectedMessage.sendToTarget();
            }
        }
    }

    public interface BleManagerListener {
        void onEvent(int event);
    }
}
