package com.cloudspace.ardrobot;

import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;

public class Listener extends AbstractNodeMain {
    MessageListener<geometry_msgs.Twist> messageListener;
    String rosTopic;

    public Listener(MessageListener<geometry_msgs.Twist> messageListener, String rosTopic) {
        this.messageListener = messageListener;
        this.rosTopic = rosTopic;
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("ardrobot_control/listener");
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        Subscriber<geometry_msgs.Twist> subscriber = connectedNode.newSubscriber(rosTopic, geometry_msgs.Twist._TYPE);
        subscriber.addMessageListener(messageListener);
    }
}