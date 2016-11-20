package link.a0ptr.usbfai;

import android.app.Service;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.util.Log;
import android.support.v4.content.LocalBroadcastManager;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

public class SerialService extends Service {
    private final static String TAG = "SerialService";
    private final static boolean DEBUG = true;

    private static UsbSerialPort sPort = null;
    private static int[] sPortSetting = null;
    private static UsbDevice mUsbDevice = null;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;

    private final static ArrayBlockingQueue<String> sBuffer = new ArrayBlockingQueue<>(4);
    private static LocalBroadcastManager sLocalBroadcastManager = null;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {
                private Queue<String> buf = new LinkedList<String>();

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {

                    String result = new String(data);

                    // received messages must be newline-terminated
                    if (!result.contains("\n")) {
                        // collect until \n is received
                        buf.add(result);

                    } else {
                        // combine fragmented data
                        if (buf.peek() != null) {
                            Iterator it = buf.iterator();
                            String str = new String();
                            while(it.hasNext())
                            {
                                str += (String)it.next();
                            }
                            result = str + result;
                        }

                        // store data in a circular buffer
                        if (!sBuffer.offer(result)) {
                            sBuffer.poll();
                            sBuffer.offer(result);
                        }
                    }
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate()");
        sLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy()");

        Intent in = new Intent(Constants.ACTION_STATUS);
        in.putExtra(Constants.EXTRA_PARAM1, "No serial device.");
        in.putExtra(Constants.EXTRA_PARAM2, "");
        in.putExtra(Constants.EXTRA_PARAM3, "false");
        sLocalBroadcastManager.sendBroadcast(in);

        unregisterReceiver(mReceiver);
        sPort = null;
        sPortSetting = null;
    }

    public static boolean isUsbConnected() {
        return (sPort != null);
    }

    public static void startService(Context context, UsbDevice device, int baud, int data, int stop,
                                    int parity, boolean dtr, boolean rts) {
        Intent intent = new Intent(context, SerialService.class);
        intent.setAction(Constants.ACTION_CONNECT);
        if (device != null) intent.putExtra(Constants.EXTRA_PARAM1, device);
        int[] settings = {baud, data, stop, parity, (dtr) ? 1 : 0, (rts) ? 1 : 0};
        intent.putExtra(Constants.EXTRA_PARAM2, settings);
        context.startService(intent);
    }

    public static void transmit(Context context , String data){
        if (!data.isEmpty()) {
            Intent intent = new Intent(context, SerialService.class);
            intent.setAction(Constants.ACTION_TRANSMIT);
            intent.putExtra(Constants.EXTRA_PARAM1, data);
            context.startService(intent);
        }
    }

    public static String receive(){
        try {
            return sBuffer.remove();
        } catch (NoSuchElementException e) {
            return "";
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand() " + intent + " " + flags + " " + startId);

        final String action = intent.getAction();

        if (sPort != null) {
            if (Constants.ACTION_TRANSMIT.equals(action)) {
                final String param = intent.getStringExtra(Constants.EXTRA_PARAM1);
                mSerialIoManager.writeAsync(param.getBytes());

            } else if (Constants.ACTION_RECEIVE.equals(action)) {
                final String param = intent.getStringExtra(Constants.EXTRA_PARAM1);
                // TODO:
            }

            // try connecting
        } else if (Constants.ACTION_CONNECT.equals(action)){
            final UsbDevice dev = (UsbDevice) intent.getParcelableExtra(Constants.EXTRA_PARAM1);
            sPortSetting = intent.getExtras().getIntArray(Constants.EXTRA_PARAM2);
            handleActionConnect(dev);

            // handle callback from UsbManager
        } else if (action == null) {
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                mUsbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                // start SerialInputOutputManager
                if (!startIoManager(mUsbDevice)) {
                    Log.e(TAG, "Failed to start SerialInputOutputManager.");
                    stopSelf();
                    mUsbDevice = null;
                }

            } else {
                Log.e(TAG, "Failed to get usb permission.");
                stopSelf();
            }
        }

        return Service.START_REDELIVER_INTENT;
    };

    private UsbSerialDriver getSerialDriver(UsbDevice device) {
        final UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // list all present drivers
        final List<UsbSerialDriver> availableDrivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

        if (availableDrivers.isEmpty()) {
            return null;
        }

        // use the first available driver
        UsbSerialDriver driver = availableDrivers.get(0);

        // or find the driver for the attached device
        if (device != null) {
            Iterator<UsbSerialDriver> driverIterator = availableDrivers.iterator();
            while (driverIterator.hasNext()) {
                UsbSerialDriver x = driverIterator.next();
                if (x.getDevice().equals(device)) {
                    driver = x;
                    break;
                }
            }
        }

        return driver;
    }

    private boolean startIoManager(UsbDevice usbDevice) {

        String driverName = null;
        String deviceID = null;

        final UsbSerialDriver driver = getSerialDriver(usbDevice);
        if (driver != null) {
            if (DEBUG) Log.d(TAG, "DriverName: " + driver.getClass().getSimpleName());
            // use the first port
            sPort = driver.getPorts().get(0);

            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(usbDevice);
            if (connection == null) {
                Log.e(TAG, "Opening device failed");
                driverName = new String("Opening device failed");
                sPort = null;
            } else {
                try {
                    sPort.open(connection);

                    if (sPortSetting == null) {
                        // default setting: 9600,8,n,1
                        sPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                        sPort.setDTR(false);
                        sPort.setRTS(false);
                        sPortSetting = new int[]{9600, 8, UsbSerialPort.STOPBITS_1,
                                UsbSerialPort.PARITY_NONE, 0, 0};
                    } else {
                        sPort.setParameters(sPortSetting[0], sPortSetting[1], sPortSetting[2], sPortSetting[3]);
                        sPort.setDTR(sPortSetting[4] != 0);
                        sPort.setRTS(sPortSetting[5] != 0);
                    }

                    sPort.purgeHwBuffers(true, true);

                } catch (IOException e) {
                    Log.e(TAG, "Error opening device: " + e.getMessage(), e);
                    driverName = new String("Error opening device: " + e.getMessage());
                    try {
                        sPort.close();
                    } catch (IOException e2) {
                        // Ignore.
                    }
                    sPort = null;
                }

                if (sPort != null) {
                    mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
                    mExecutor.submit(mSerialIoManager);
                    driverName = sPort.getClass().getSimpleName();
                    deviceID = String.format("Vendor %s Product %s",
                            HexDump.toHexString((short) usbDevice.getVendorId()),
                            HexDump.toHexString((short) usbDevice.getProductId()));
                }
            }
        } else {
            Log.e(TAG, "Failed to get serial driver");
            driverName = new String("Failed to get serial driver.");
            sPort = null;
        }

        Intent in = new Intent(Constants.ACTION_STATUS);
        in.putExtra(Constants.EXTRA_PARAM1, driverName);
        in.putExtra(Constants.EXTRA_PARAM2, deviceID);
        in.putExtra(Constants.EXTRA_PARAM3, String.valueOf(sPort != null));
        sLocalBroadcastManager.sendBroadcast(in);

        return sPort != null;
    }

    private void handleActionConnect(UsbDevice dev) {

        UsbSerialDriver driver = getSerialDriver(dev);
        if (driver == null) {
            if (DEBUG) Log.d(TAG, "No usb serial device present!");
            return;
        }
        // use the first available port
        final UsbDevice device = driver.getDevice();

        if (DEBUG) {
            Log.d(TAG, "VendorId: " + device.getVendorId());
            Log.d(TAG, "ProductId: " + device.getProductId());
            Log.d(TAG, "DeviceName: " + device.getDeviceName());
            Log.d(TAG, "DeviceId: " + device.getDeviceId());
            Log.d(TAG, "DeviceClass: " + device.getDeviceClass());
            Log.d(TAG, "DeviceSubclass: " + device.getDeviceSubclass());
            Log.d(TAG, "InterfaceCount: " + device.getInterfaceCount());
            Log.d(TAG, "DeviceProtocol: " + device.getDeviceProtocol());
            Log.d(TAG, "DriverName: " + driver.getClass().getSimpleName());
        }

        // obtain permission to use the usb device
        final Intent startIntent = new Intent(this, SerialService.class);
        final PendingIntent mPendingIntent = PendingIntent.getService(this, 0, startIntent, 0);
        final UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mUsbManager.requestPermission(device, mPendingIntent);
    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "onReceive() " + action);

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (mUsbDevice.equals(device)) {

                    stopSelf();
                    mUsbDevice = null;
                }
            }
        }
    };
}
