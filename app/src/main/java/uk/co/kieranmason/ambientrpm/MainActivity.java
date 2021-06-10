package uk.co.kieranmason.ambientrpm;

import android.annotation.SuppressLint;
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
    private final String lightAddress = "BE:FF:E4:00:A9:83", obdAddress = "00:1D:A5:68:98:8B";

    private BluetoothAdapter bluetoothAdapter;
    private LightConnection lightConnection;
    private OBD2Connection obdConnection;

    private TextView lightStatusLabel, obdStatusLabel, rpmLabel;
    private Button lightRandomiseButton;

    private static int calculateColourFromRPM(int rpm) {
        // Map 1000, 1500 and 2000 RPM to green, yellow and red respectively.
        // Keeping the revs low for testing - you're welcome neighbours!
        final int colourA, colourB;
        final float t;

        if (rpm <= 1000)
            return Color.GREEN;
        else if (rpm <= 1500) {
            // Lerp between GREEN and YELLOW.
            colourA = Color.GREEN;
            colourB = Color.YELLOW;
            t = (rpm - 1000f) / 500f;
        } else if (rpm <= 2000) {
            // Lerp between YELLOW and RED.
            colourA = Color.YELLOW;
            colourB = Color.RED;
            t = (rpm - 1500f) / 500f;
        } else
            return Color.RED;

        return lerpColour(colourA, colourB, t);
    }

    public static int lerpColour(int colourA, int colourB, float t) {
        final int red1 = Color.red(colourA), red2 = Color.red(colourB),
                blue1 = Color.blue(colourA), blue2 = Color.blue(colourB),
                green1 = Color.green(colourA), green2 = Color.green(colourB),
                alpha1 = Color.alpha(colourA), alpha2 = Color.alpha(colourB);

        final int red = lerp(red1, red2, t),
                green = lerp(green1, green2, t),
                blue = lerp(blue1, blue2, t),
                alpha = lerp(alpha1, alpha2, t);

        return Color.argb(alpha, red, green, blue);
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + t * (b - a));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the Bluetooth adapter.
        final BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        // Create the light connection.
        lightConnection = new LightConnection(this, lightAddress, this::onLightStateChanged);

        // Create the OBD2 connection.
        obdConnection = new OBD2Connection(this, obdAddress, this::onOBDStateChanged, this::onRPMReceived);

        // Ensure that bluetooth is enabled.
        if (!isBluetoothEnabled()) {
            requestBluetoothEnable();
        }

        lightStatusLabel = findViewById(R.id.lightsStateLabel);
        lightRandomiseButton = findViewById(R.id.lightsRandomiseButton);
        obdStatusLabel = findViewById(R.id.obdStateLabel);
        rpmLabel = findViewById(R.id.rpmLabel);
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

    private void onOBDStateChanged(OBD2Connection connection) {
        final ConnectionState state = connection.getState();
        switch (state) {
            case DISCONNECTED:
                setTextViewString(obdStatusLabel, R.string.disconnected);
                break;
            case CONNECTING:
                setTextViewString(obdStatusLabel, R.string.connecting);
                break;
            case CONNECTED:
                setTextViewString(obdStatusLabel, R.string.connected);
                break;
        }
    }

    @SuppressLint("SetTextI18n")
    private void onRPMReceived(OBD2Connection connection) {
        final int rpm = connection.getRPM();

        // Calculate the mapped colour and send it to the light connection.
        final int colour = calculateColourFromRPM(rpm);
        lightConnection.setColour(colour);

        runOnUiThread(() -> rpmLabel.setText("RPM: " + rpm));
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
        final int r = random.nextInt(256),
                g = random.nextInt(256),
                b = random.nextInt(256);
        final int colour = Color.rgb(r, g, b);

        // Send this via the connection to the light.
        lightConnection.setColour(colour);

        // Visualise the colour on the randomise button.
        lightRandomiseButton.setBackgroundColor(colour);
    }

    public void onOBDConnectPressed(View view) {
        if (!isBluetoothEnabled()) {
            requestBluetoothEnable();
            return;
        }

        obdConnection.connect();
    }
}