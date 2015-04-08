package com.cloudspace.ardrobot;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.usb.UsbAccessory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.cloudspace.ardrobot.util.BaseActivity;
import com.cloudspace.ardrobot.util.CustomRosCameraPreviewView;
import com.cloudspace.rosserial_android.ROSSerialADK;
import com.cloudspace.rosserial_java.NodeConnectionUtils;
import com.cloudspace.rosserial_java.TopicRegistrationListener;

import org.ros.address.InetAddressFactory;
import org.ros.node.ConnectedNode;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import rosserial_msgs.TopicInfo;


public class ExternalCoreActivity extends BaseActivity {


    private int cameraId = 0;
    private CustomRosCameraPreviewView rosCameraPreviewView;

    TextView mMasterUriOutput, rosTextView;

    UsbAccessory mAccessory;

    ConnectedNode connectedNode;
    ROSSerialADK adk;

    Handler sizeCheckHandy = new Handler();

    HashMap<TopicInfo, Boolean> interestedTopics;

    AlertDialog errorDialog;

    private TopicRegistrationListener topicRegisteredListener = new TopicRegistrationListener() {
        @Override
        public void onNewTopic(final TopicInfo t) {
            interestedTopics.put(t, true);
            handleNodesReady();
        }
    };

    Handler errorHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (isActive()) {
                if (msg.getData() != null && msg.getData().get("error") != null) {
                    String title;
                    String message = (String) msg.getData().get("error");
                    switch (msg.what) {
                        case ROSSerialADK.ERROR_ACCESSORY_CANT_CONNECT:
                            title = "Unable to connect";
                            break;
                        case ROSSerialADK.ERROR_ACCESSORY_NOT_CONNECTED:
                            title = "Unable to communicate";
                            break;
                        default:
                        case ROSSerialADK.ERROR_UNKNOWN:
                            title = "Unknown error";
                            break;
                    }
                    if (errorDialog == null) {
                        errorDialog = new AlertDialog.Builder(ExternalCoreActivity.this).setTitle(title).setMessage(message).create();
                    } else {
                        errorDialog.setTitle(title);
                        errorDialog.setMessage(message);
                    }
                    errorDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Close", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finish();
                            startActivity(new Intent(ExternalCoreActivity.this, AccessoryActivity.class));
                        }
                    });
                    errorDialog.show();
                }
            }
        }
    };

    Runnable sizeCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (rosCameraPreviewView.height == -1 || rosCameraPreviewView.width == -1) {
                sizeCheckHandy.postDelayed(this, 100);
            } else {
                Camera camera = Camera.open(cameraId);
                rosCameraPreviewView.setCamera(camera);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_external_master);
        interestedTopics = new HashMap<>();

        if (getIntent().hasExtra("accessory")) {
            mAccessory = (UsbAccessory) getIntent().getParcelableExtra("accessory");
        } else {
            ROSSerialADK.sendError(errorHandler, ROSSerialADK.ERROR_ACCESSORY_NOT_CONNECTED, "No accessory connected.");
        }

        mMasterUriOutput = (TextView) findViewById(R.id.master_uri);

        rosCameraPreviewView = (CustomRosCameraPreviewView) findViewById(R.id.ros_camera_preview_view);
        rosCameraPreviewView.setQuality(20);
        rosTextView = (TextView) findViewById(R.id.text);

        attemptToSetAdk();
    }

    private void attemptToSetAdk() {

        if (connectedNode != null && mAccessory != null) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        adk = new ROSSerialADK(errorHandler, ExternalCoreActivity.this, connectedNode, mAccessory);
                    } catch (Exception e) {
                        ROSSerialADK.sendError(errorHandler, ROSSerialADK.ERROR_ACCESSORY_CANT_CONNECT, e.getMessage());
                        return;
                    }
                    for (TopicInfo tI : adk.getPublications()) {
                        Log.d("PUBLISHER FROM SERIAL", tI.getTopicName());
                        interestedTopics.put(tI, false);
                    }
                    for (TopicInfo tI : adk.getSubscriptions()) {
                        Log.d("SUBSCRIBER FROM SERIAL", tI.getTopicName());
                        interestedTopics.put(tI, false);
                    }
                    adk.setOnSubscriptionCB(topicRegisteredListener);
                    adk.setOnSubscriptionCB(topicRegisteredListener);
                    ((ViewFlipper) findViewById(R.id.flipper)).setDisplayedChild(1);
                }
            });
        } else if (connectedNode != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((ViewFlipper) findViewById(R.id.flipper)).setDisplayedChild(1);
                }
            });
        }
    }

    public void handleNodesReady() {
        boolean ready = false;
        Iterator it = interestedTopics.entrySet().iterator();
        final StringBuilder topicString = new StringBuilder();
        topicString.append("Registered: ");
        while (it.hasNext()) {
            Map.Entry<TopicInfo, Boolean> topic = (Map.Entry) it.next();
            topicString.append(topic.getKey().getTopicName() + "\n");
            if (!topic.getValue()) {
                break;
            }
            ready = true;
        }

        if (ready) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    rosTextView.setText(topicString.toString());
                    Toast.makeText(ExternalCoreActivity.this, "All subs and pubs registered.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    NodeConnectionUtils connectionUtils = new NodeConnectionUtils("node", new NodeConnectionUtils.OnNodeConnectedListener() {
        @Override
        public void onNodeConnected(ConnectedNode node) {
            connectedNode = node;
            attemptToSetAdk();
        }
    });

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        if (mAccessory == null) {
            return;
        }
        try {
            NodeConfiguration config = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostName())
                    .setMasterUri(getMasterUri());
            config.setMasterUri(getMasterUri());

            NodeConfiguration cameraConfiguration = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostName())
                    .setMasterUri(getMasterUri());
//            setCameraDisplayOrientation(camera);

            nodeMainExecutor.execute(connectionUtils, config);
            nodeMainExecutor.execute(rosCameraPreviewView, cameraConfiguration);
        } catch (Exception e) {
            e.printStackTrace();
        }

        sizeCheckHandy.post(sizeCheckRunnable);
    }


    private void setCameraDisplayOrientation(Camera camera) {
        Camera.CameraInfo info =
                new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        camera.setDisplayOrientation((info.orientation - degrees + 360) % 360);
    }
}