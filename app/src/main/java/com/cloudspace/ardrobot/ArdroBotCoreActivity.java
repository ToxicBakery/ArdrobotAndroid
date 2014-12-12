package com.cloudspace.ardrobot;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.lang.ArrayUtils;
import org.ros.RosCore;
import org.ros.address.InetAddressFactory;
import org.ros.android.MessageCallable;
import org.ros.android.NodeMainExecutorService;
import org.ros.android.RosActivity;
import org.ros.android.view.RosTextView;
import org.ros.android.view.camera.RosCameraPreviewView;
import org.ros.exception.RosRuntimeException;
import org.ros.message.MessageListener;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import geometry_msgs.Vector3;


public class ArdroBotCoreActivity extends RosActivity implements MessageListener<geometry_msgs.Twist> {

    private static final String TAG = "Ardrobot";
    private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKit.action.USB_PERMISSION";
    ParcelFileDescriptor mFileDescriptor;
    FileInputStream mInputStream;
    FileOutputStream mOutputStream;
    UsbAccessory mAccessory;
    private UsbManager mUsbManager;
    private Listener listener;
    private int cameraId;
    private RosCameraPreviewView rosCameraPreviewView;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory "
                                + accessory);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (accessory != null && accessory.equals(mAccessory)) {
                    closeAccessory();
                }
            }
        }
    };
    private RosTextView<geometry_msgs.Twist> rosTextView;
    private URI mMasterUri;
    RosCore core;
    public ArdroBotCoreActivity() {
        super("ArdroBot", "ArdroBot");
    }

    private void openAccessory(UsbAccessory accessory) {
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            Log.d(TAG, "accessory opened");
        } else {
            Log.d(TAG, "accessory open fail");
        }
    }

    private void closeAccessory() {
        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rosCameraPreviewView = (RosCameraPreviewView) findViewById(R.id.ros_camera_preview_view);
        rosTextView = (RosTextView<geometry_msgs.Twist>) findViewById(R.id.text);
        rosTextView.setTopicName("/virtual_joystick/cmd_vel");
        rosTextView.setMessageType(geometry_msgs.Twist._TYPE);


        mUsbManager = (UsbManager) getSystemService(USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        if (core == null) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
                    try {
                        WifiInfo wifiinfo = wm.getConnectionInfo();
                        byte[] myIPAddress = BigInteger.valueOf(wifiinfo.getIpAddress()).toByteArray();
                        ArrayUtils.reverse(myIPAddress);
                        InetAddress myInetIP = InetAddress.getByAddress(myIPAddress);
                        String myIP = myInetIP.getHostAddress();
                        String fullHostName = "http://" + myIP + ":11311";
                        core = RosCore.newPublic(myIP, 11311);
                        core.start();
                        mMasterUri = core.getUri();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }.execute();
        }
    }

    @Override
    public void startMasterChooser() {
        nodeMainExecutorService.setMasterUri(mMasterUri);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ArdroBotCoreActivity.this.init(nodeMainExecutorService);
                return null;
            }
        }.execute();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mInputStream != null && mOutputStream != null) {
            return;
        }

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                openAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory, mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "mAccessory is null");
        }

        rosTextView.setMessageToStringCallable(new MessageCallable<String, geometry_msgs.Twist>() {
            @Override
            public String call(geometry_msgs.Twist message) {
                Vector3 vector = message.getLinear();
                double x = vector.getX();
                double y = vector.getY();
                StringBuilder sb = new StringBuilder();
                sb.append(x).append(":").append(y);
                return sb.toString();
            }
        });

    }

    @Override
    public void onPause() {
        super.onPause();
//        closeAccessory();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        core.shutdown();
        super.onDestroy();
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        listener = new Listener(this, "/virtual_joystick/cmd_vel");
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostName());
        nodeConfiguration.setMasterUri(getMasterUri());

        nodeMainExecutor.execute(listener, nodeConfiguration);
        nodeMainExecutor.execute(rosTextView, nodeConfiguration);

//        rosCameraPreviewView.setCamera(Camera.open(cameraId));
//        nodeMainExecutor.execute(rosCameraPreviewView, nodeConfiguration);
    }

    public void writeToBoard(Direction direction) {
        byte[] buffer = new byte[direction.directionByte];

        if (mOutputStream != null) {
            try {
                mOutputStream.write(buffer);
                Toast.makeText(this, "Sent command " + direction.directionCommand, Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(this, "Failed to send command " + direction.directionCommand + " " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Got " + direction.directionCommand + ", but not connected to accessory", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onNewMessage(final geometry_msgs.Twist message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Vector3 vector = message.getLinear();
                double x = vector.getX();
                double y = vector.getY();
                Direction command;
                if (x > 0) {
                    command = Direction.FORWARD;
                } else if (x < 0) {
                    command = Direction.BACK;
                } else if (y < 0) {
                    command = Direction.RIGHT;
                } else if (y > 0) {
                    command = Direction.LEFT;
                } else {
                    command = Direction.STOP;
                }
                writeToBoard(command);
                StringBuilder sb = new StringBuilder();
                sb.append(x).append(":").append(y);
                String coords = sb.toString();
                Log.v(TAG, "Coordinates: " + coords);

            }
        });
    }
}
