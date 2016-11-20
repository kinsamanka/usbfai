package link.a0ptr.usbfai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class SerialActivity extends Activity {
    private final static String TAG = "SerialActivity";
    private final static boolean DEBUG = true;

    private static LocalBroadcastManager sLocalBroadcastManager = null;
    private static String txData = null;
    private final static int REQUEST_TX = 1;
    private final static int REQUEST_RX = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (DEBUG) Log.d(TAG, "onCreate(): " + action);

        if (action.equals("android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
            // process only if service is not yet running
            if (!SerialService.isUsbConnected()) {
                if (!MainApplication.isMainCreated()) {
                    // create a window if MainActivity is not running
                    Intent in = new Intent(this, MainActivity.class);
                    in.setAction(Constants.ACTION_CONNECT);
                    in.putExtra(Constants.EXTRA_PARAM1, action);
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    in.putExtra(Constants.EXTRA_PARAM2, device);
                    startActivity(in);

                } else {
                    // otherwise, just send a bradcast to update the device
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Intent in = new Intent(Constants.ACTION_UPDATE_DEVICE);
                    in.putExtra(Constants.EXTRA_PARAM1, device);
                    sLocalBroadcastManager.sendBroadcast(in);
                }
            }

            finish();

        } else {

            if (!SerialService.isUsbConnected()) {

                Intent in = new Intent(this, MainActivity.class);
                in.setAction(Constants.ACTION_CONNECT);
                in.putExtra(Constants.EXTRA_PARAM1, action);

                if (action.equals(Constants.ACTION_TRANSMIT)) {
                    txData = intent.getDataString();
                    startActivityForResult(in, REQUEST_TX);

                } else if (action.equals(Constants.ACTION_RECEIVE)) {
                    startActivityForResult(in, REQUEST_RX);

                } else {
                    setResult(RESULT_CANCELED);
                    finish();
                }

            } else {

                if (action.equals(Constants.ACTION_TRANSMIT)) {
                    SerialService.transmit(this, intent.getDataString());
                    setResult(RESULT_OK);

                } else if (action.equals(Constants.ACTION_RECEIVE)) {
                    setResult(RESULT_OK, rxIntent());

                } else {
                    setResult(RESULT_CANCELED);
                }

                finish();
            }
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (DEBUG) Log.d(TAG, "onActivityResult(): " + requestCode + " - " + resultCode);

        if (requestCode == REQUEST_TX) {
            if (resultCode == RESULT_OK)
                SerialService.transmit(this, txData);
            setResult(resultCode);

        } else if (requestCode == REQUEST_RX) {
            if (resultCode == RESULT_OK)
                setResult(RESULT_OK, rxIntent());
            else
                setResult(RESULT_CANCELED);

        } else {
            setResult(RESULT_CANCELED);
        }

        finish();
    }

    private static Intent rxIntent() {
        Bundle bundle = new Bundle();
        bundle.putString("APP_INVENTOR_RESULT", SerialService.receive());
        Intent intent = new Intent();
        intent.putExtras(bundle);
        return intent;
    }
}
