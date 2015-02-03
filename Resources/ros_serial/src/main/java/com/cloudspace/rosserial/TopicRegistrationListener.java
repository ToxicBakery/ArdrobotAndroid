package com.cloudspace.rosserial;


import rosserial_msgs.TopicInfo;

/**
 * Listener for notification of new subscriptions or publications.
 *
 * @author Adam Stambler
 */
public interface TopicRegistrationListener {
	/**
	 * A new topic has come in.
	 * 
	 * @param t Information about the new topic.
	 */
	void onNewTopic(TopicInfo t);
}