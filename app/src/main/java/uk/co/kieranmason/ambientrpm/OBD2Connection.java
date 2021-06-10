package uk.co.kieranmason.ambientrpm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import androidx.core.util.Consumer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import br.ufrn.imd.obd.commands.ObdCommandGroup;
import br.ufrn.imd.obd.commands.engine.RPMCommand;
import br.ufrn.imd.obd.commands.protocol.EchoOffCommand;
import br.ufrn.imd.obd.commands.protocol.LineFeedOffCommand;
import br.ufrn.imd.obd.commands.protocol.SelectProtocolCommand;
import br.ufrn.imd.obd.commands.protocol.TimeoutCommand;
import br.ufrn.imd.obd.enums.ObdProtocols;

public class OBD2Connection {
    private final BluetoothAdapter adapter;
    private final String address;

    private final Consumer<OBD2Connection> onStatusChange, onRPMReceived;
    private final UUID serialPortProfile = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private ConnectionState state = ConnectionState.DISCONNECTED;
    private int rpm = 0;

    private BluetoothSocket socket;
    private Thread thread;

    public OBD2Connection(Context context, String address, Consumer<OBD2Connection> stateChangeCallback, Consumer<OBD2Connection> rpmChangeCallback) {
        this.address = address;

        this.onStatusChange = stateChangeCallback;
        this.onRPMReceived = rpmChangeCallback;

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

        try {
            socket.close();
        } catch (IOException e) {
            // There was an error with the socket. Ensure the thread is interrupted.
            thread.interrupt();
        }
    }

    public void connect() {
        // Don't attempt to connect if the device doesn't have a bluetooth adapter.
        if (adapter == null)
            return;

        // Only attempt to connect if we're disconnected. (I.e. not connected/trying to connect)
        if (state != ConnectionState.DISCONNECTED)
            return;

        setState(ConnectionState.CONNECTING);

        thread = new Thread(this::threadMethod);
        thread.start();
    }

    public boolean connected() {
        return state == ConnectionState.CONNECTED;
    }

    private void onStateChange() {
        onStatusChange.accept(this);
    }

    private void threadMethod() {
        final BluetoothDevice device = adapter.getRemoteDevice(address);
        try {
            // Create and attempt to connect to the socket.
            socket = device.createInsecureRfcommSocketToServiceRecord(serialPortProfile);
            socket.connect();

            final InputStream in = socket.getInputStream();
            final OutputStream out = socket.getOutputStream();

            // Initialise the OBD2 adapter with a 100ms timeout.
            ObdCommandGroup obdCommands = new ObdCommandGroup();
            obdCommands.add(new EchoOffCommand());
            obdCommands.add(new LineFeedOffCommand());
            obdCommands.add(new TimeoutCommand(100 / 4));
            obdCommands.add(new SelectProtocolCommand(ObdProtocols.AUTO));

            obdCommands.run(in, out);

            setState(ConnectionState.CONNECTED);

            // The socket connection has been established, continuously loop, sending and receiving
            // the RPM command, until the socket or thread dies.
            final RPMCommand rpmCommand = new RPMCommand();
            while (socket.isConnected() && !Thread.currentThread().isInterrupted()) {
                rpmCommand.run(in, out);
                rpm = rpmCommand.getRPM();
                onRPMReceived.accept(this);
            }
        } catch (IOException | InterruptedException e) {
            // An error occurred, disconnect.
            disconnect();
        }
    }

    public int getRPM() {
        return rpm;
    }
}
