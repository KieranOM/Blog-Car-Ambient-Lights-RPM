package uk.co.kieranmason.ambientrpm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.core.util.Consumer;

import java.util.UUID;

public class LightConnection {
    private final Context context;
    private final BluetoothAdapter adapter;
    private final String address;

    private final Consumer<LightConnection> stateChangeCallback;
    private final byte[] payloadBuffer = new byte[]{0x7e, 0x07, 0x05, 0x03, 0, 0, 0, 0x10, (byte) 0xef};

    private ConnectionState state = ConnectionState.DISCONNECTED;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;

    public LightConnection(Context context, String address, Consumer<LightConnection> stateChangeCallback) {
        this.context = context;
        this.address = address;
        this.stateChangeCallback = stateChangeCallback;

        final BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        this.adapter = manager == null ? null : manager.getAdapter();
    }

    public ConnectionState getState() {
        return state;
    }

    private void setState(ConnectionState state) {
        if (this.state == state)
            return;

        this.state = state;

        onStateChange();
    }

    public void disconnect() {
        // Don't attempt to disconnect if we're already disconnected...
        if (state == ConnectionState.DISCONNECTED)
            return;

        setState(ConnectionState.DISCONNECTED);

        // Trigger the disconnect, this will cause the callback in the connection handler.
        gatt.disconnect();
    }

    public void connect() {
        // Don't attempt to connect if the device doesn't have a bluetooth adapter.
        if (adapter == null)
            return;

        // Only attempt to connect if we're disconnected. (I.e. not connected/trying to connect)
        if (state != ConnectionState.DISCONNECTED)
            return;

        setState(ConnectionState.CONNECTING);

        // Get the remote device by its address and connect to it.
        BluetoothDevice device = adapter.getRemoteDevice(address);
        device.connectGatt(context, false, new LightConnectionHandler(this));
    }

    public boolean connected() {
        return state == ConnectionState.CONNECTED;
    }

    private byte[] getColourPayload(byte red, byte green, byte blue) {
        // Put the RGB values into the payload at the right positions.
        payloadBuffer[4] = red;
        payloadBuffer[5] = green;
        payloadBuffer[6] = blue;

        return payloadBuffer;
    }

    private void onStateChange() {
        stateChangeCallback.accept(this);
    }

    public void setColour(int colour) {
        // Don't attempt to send any data when the characteristic isn't set up.
        if (!connected())
            return;

        // Unpack the colour into its bytes.
        final byte red = (byte) ((colour >> 16) & 0xff),
                green = (byte) ((colour >> 8) & 0xff),
                blue = (byte) ((colour) & 0xff);

        // Update the payload buffer with the correct parameters.
        byte[] payload = getColourPayload(red, green, blue);

        // Write to the characteristic and send it.
        characteristic.setValue(payload);
        gatt.writeCharacteristic(characteristic);
    }

    private static class LightConnectionHandler extends BluetoothGattCallback {
        private static final UUID serviceID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
                characteristicID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");

        private final LightConnection connection;

        public LightConnectionHandler(LightConnection connection) {
            this.connection = connection;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED && gatt.discoverServices()) {
                // Connected. Just need to get the services now.
                connection.setState(ConnectionState.CONNECTING);
                gatt.discoverServices();
            } else {
                // Disconnected. Clear the connection values.
                connection.setState(ConnectionState.DISCONNECTED);
                connection.gatt = null;
                connection.characteristic = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(serviceID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicID);
                    if (characteristic != null) {
                        // Successfully connected. Set up the connection values.
                        connection.gatt = gatt;
                        connection.characteristic = characteristic;
                        connection.setState(ConnectionState.CONNECTED);
                        return;
                    }
                }
            }

            // Default non-returned case - disconnect.
            connection.disconnect();
        }
    }
}
