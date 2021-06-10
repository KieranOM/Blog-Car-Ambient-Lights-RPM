package uk.co.kieranmason.ambientrpm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private final static int REQUEST_ENABLE_BT = 1;
    private final Random random = new Random();
    private final String lightAddress = "BE:FF:E4:00:A9:83";

    private BluetoothAdapter bluetoothAdapter;
    private LightConnection lightConnection;

    private TextView lightStatusLabel;
    private Button lightRandomiseButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the Bluetooth adapter.
        final BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        // Create the light connection.
        lightConnection = new LightConnection(this, lightAddress, this::onLightStateChanged);

        // Ensure that bluetooth is enabled.
        if (!isBluetoothEnabled()) {
            requestBluetoothEnable();
        }

        lightStatusLabel = findViewById(R.id.lightsStateLabel);
        lightRandomiseButton = findViewById(R.id.lightsRandomiseButton);
    }

    private void onLightStateChanged(LightConnection connection) {
        final ConnectionState state = lightConnection.getState();
        switch (state) {
            case DISCONNECTED:
                setTextViewString(lightStatusLabel, R.string.disconnected);
                break;
            case CONNECTING:
                setTextViewString(lightStatusLabel, R.string.connecting);
                break;
            case CONNECTED:
                setTextViewString(lightStatusLabel, R.string.connected);
                break;
        }
    }

    private boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    private void requestBluetoothEnable() {
        final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    public void onLightsConnectPressed(View view) {
        // Re-prompt the user to enable bluetooth if it's disabled.
        if (!isBluetoothEnabled()) {
            requestBluetoothEnable();
            return;
        }

        lightConnection.connect();
    }

    private void setTextViewString(TextView view, int stringId) {
        // Run the text setting on the UI thread.
        runOnUiThread(() -> view.setText(stringId));
    }

    public void onLightsRandomisePressed(View view) {
        // Don't do anything if we're not connected to the lights.
        if (!lightConnection.connected())
            return;

        // Generate a random colour via its channels.
        final int r = random.nextInt(255),
                g = random.nextInt(255),
                b = random.nextInt(255);
        final int colour = Color.rgb(r, g, b);

        // Send this via the connection to the light.
        lightConnection.setColour(colour);

        // Visualise the colour on the randomise button.
        lightRandomiseButton.setBackgroundColor(colour);
    }
}