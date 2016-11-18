package link.a0ptr.usbfai;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "USBFAI";
    private final static boolean DEBUG = true;

    private TextView mDriverTextView;
    private TextView mDeviceTextView;
    private Spinner mSpinnerBaudRate;
    private Spinner mSpinnerDataBits;
    private Spinner mSpinnerStopBits;
    private Spinner mSpinnerParity;
    private CheckBox mCheckDTR;
    private CheckBox mCheckRTS;

    private static UsbDevice usbDevice = null;
    private static int baudRate = 0;
    private static int dataBits = 0;
    private static int stopBits = 0;
    private static int parity = 0;
    private static boolean dtr = false;
    private static boolean rts = false;
    private static String driverName = null;
    private static String deviceID = null;
    private static Menu menu = null;

    private final static int[] dataBitsArray = {UsbSerialPort.DATABITS_5, UsbSerialPort.DATABITS_6,
            UsbSerialPort.DATABITS_7, UsbSerialPort.DATABITS_8};
    private final static int[] stopBitsArray = {UsbSerialPort.STOPBITS_1, UsbSerialPort.STOPBITS_1_5,
            UsbSerialPort.STOPBITS_2};
    private final static int[] parityArray = {UsbSerialPort.PARITY_NONE, UsbSerialPort.PARITY_ODD,
            UsbSerialPort.PARITY_EVEN, UsbSerialPort.PARITY_MARK,
            UsbSerialPort.PARITY_SPACE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedPref = getSharedPreferences("portSettings", MODE_PRIVATE);
        baudRate = sharedPref.getInt("baudRate", 9600);
        dataBits = sharedPref.getInt("dataBits", UsbSerialPort.DATABITS_8);
        stopBits = sharedPref.getInt("stopBits", UsbSerialPort.STOPBITS_1);
        parity = sharedPref.getInt("parity", UsbSerialPort.PARITY_NONE);
        dtr = sharedPref.getBoolean("dtr", false);
        rts = sharedPref.getBoolean("rts", false);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (DEBUG) Log.d(TAG, "onCreate(): " + action);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setupWidgets();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        SharedPreferences sharedPref = getSharedPreferences("portSettings", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("baudRate", baudRate);
        editor.putInt("dataBits", dataBits);
        editor.putInt("stopBits", stopBits);
        editor.putInt("parity", parity);
        editor.putBoolean("dtr", dtr);
        editor.putBoolean("rts",rts);
        editor.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "The onResume() event");

        if (!UsbfaiService.isUsbConnected()) {
            mDriverTextView.setText("No serial device.");
            mDeviceTextView.setText("");
            updateWidgets(true);
        } else {
            mDriverTextView.setText(driverName);
            mDeviceTextView.setText(deviceID);
            updateWidgets(false);
        }

        for (int i=0;i<mSpinnerBaudRate.getCount();i++){
            if (mSpinnerBaudRate.getItemAtPosition(i).equals(String.valueOf(baudRate))){
                mSpinnerBaudRate.setSelection(i);
                break;
            }
        }

        updateSpinner(mSpinnerDataBits, dataBitsArray, dataBits);
        updateSpinner(mSpinnerStopBits, stopBitsArray, stopBits);
        updateSpinner(mSpinnerParity, parityArray, parity);
        mCheckRTS.setChecked(rts);
        mCheckDTR.setChecked(dtr);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.ACTION_UPDATE);
        filter.addAction(Constants.ACTION_STATUS);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (DEBUG) Log.d(TAG, "The onPause() event");

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        if (UsbfaiService.isUsbConnected())
            menu.getItem(0).setIcon(R.drawable.ic_action_exit);
        else
            menu.getItem(0).setIcon(R.drawable.ic_action_connect);

        this.menu = menu;

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_log) {
            // TODO: display logged data
            return true;

        } else if (id == R.id.action_connect) {

            if (!UsbfaiService.isUsbConnected())
                UsbfaiService.startService(this, usbDevice, baudRate, dataBits, stopBits, parity,
                        dtr, rts);
            else
                stopService(new Intent(this, UsbfaiService.class));

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupWidgets() {

        mDriverTextView = (TextView) findViewById(R.id.driverTitle);
        mDeviceTextView = (TextView) findViewById(R.id.deviceTitle);

        mSpinnerBaudRate = (Spinner) findViewById(R.id.spinnerBaudRate);
        mSpinnerDataBits = (Spinner) findViewById(R.id.spinnerDataBits);
        mSpinnerStopBits = (Spinner) findViewById(R.id.spinnerStopBits);
        mSpinnerParity = (Spinner) findViewById(R.id.spinnerParity);
        mCheckDTR = (CheckBox) findViewById(R.id.checkBoxDTR);
        mCheckRTS = (CheckBox) findViewById(R.id.checkBoxRTS);

        mSpinnerBaudRate.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        baudRate = Integer.parseInt(parent.getItemAtPosition(position).toString());
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                }
        );

        mSpinnerDataBits.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        dataBits = dataBitsArray[position];
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                }
        );

        mSpinnerStopBits.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        stopBits = stopBitsArray[position];
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                }
        );

        mSpinnerParity.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        parity = parityArray[position];
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                }
        );

        mCheckDTR.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dtr = isChecked;
            }
        });

        mCheckRTS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rts = isChecked;
            }
        });

        updateWidgets(!UsbfaiService.isUsbConnected());
    }

    private static void updateSpinner(Spinner spin, int[] arr, int val) {
        for (int i=0;i<arr.length;i++){
            if (arr[i] == val){
                spin.setSelection(i);
                return;
            }
        }
    }
    private void updateWidgets(Boolean state) {
        findViewById(R.id.spinnerBaudRate).setEnabled(state);
        findViewById(R.id.spinnerDataBits).setEnabled(state);
        findViewById(R.id.spinnerStopBits).setEnabled(state);
        findViewById(R.id.spinnerParity).setEnabled(state);
        findViewById(R.id.checkBoxDTR).setEnabled(state);
        findViewById(R.id.checkBoxRTS).setEnabled(state);
        if (menu != null) {
            if (state)
                menu.getItem(0).setIcon(R.drawable.ic_action_connect);
            else
                menu.getItem(0).setIcon(R.drawable.ic_action_exit);
        }
    }

    private BroadcastReceiver mReceiver= new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(Constants.ACTION_STATUS.equals(action)) {
                driverName = intent.getStringExtra(Constants.EXTRA_PARAM1);
                deviceID = intent.getStringExtra(Constants.EXTRA_PARAM2);
                final Boolean connected =
                        Boolean.parseBoolean(intent.getStringExtra(Constants.EXTRA_PARAM3));

                TextView view = (TextView) findViewById(R.id.driverTitle);
                view.setText(driverName);
                view = (TextView) findViewById(R.id.deviceTitle);
                view.setText(deviceID);

                updateWidgets(!connected);

            } else if(Constants.ACTION_UPDATE.equals(action)) {
                final byte[] data = intent.getStringExtra(Constants.EXTRA_PARAM1).getBytes();
                final String message = "Read " + data.length + " bytes: \n"
                        + HexDump.dumpHexString(data) + "\n\n";

            }
        }
    };
}
