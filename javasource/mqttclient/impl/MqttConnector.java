package mqttclient.impl;

import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.logging.ILogNode;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IDataType;
import com.mendix.systemwideinterfaces.core.IMendixObject;

import mqttclient.impl.MqttConnector.MqttConnection;
import mqttclient.proxies.Enum_PubOrSub;

/**
 * Created by ako on 1/9/2016.
 */
public class MqttConnector {
	private static Map<String, MqttConnection> mqttHandlers = new HashMap<String, MqttConnection>();
	protected static ILogNode logger = Core.getLogger("MqttConnector");

	public MqttConnector() { }


	public static void subscribe(String brokerHost, Long brokerPort, String brokerOrganisation, String topicName, String onMessageMicroflow, String CA, String ClientCertificate, String ClientKey, String CertificatePassword, String username, String password, mqttclient.proxies.qos QoS, long timeout) throws Exception {
		MqttConnection connection = getMqttConnection(brokerHost, brokerPort, brokerOrganisation, CA, ClientCertificate, ClientKey, CertificatePassword, username, password, timeout, topicName, Enum_PubOrSub.Subscribe);
		connection.subscribe(topicName, onMessageMicroflow, QoS);
	}

	public static void unsubscribe(String brokerHost, Long brokerPort, String brokerOrganisation, String topicName, String username) throws Exception {
		MqttConnection connection = getMqttConnection(brokerHost, brokerPort, brokerOrganisation, null, null, null, null, username, null,0, topicName, null);
		connection.unsubscribe(topicName);
	}

	public static void publish(String brokerHost, Long brokerPort, String brokerOrganisation, String topicName, String message, String CA, String ClientCertificate, String ClientKey, String CertificatePassword, String username, String password, mqttclient.proxies.qos QoS, long timeout) throws Exception {
		MqttConnection connection = getMqttConnection(brokerHost, brokerPort, brokerOrganisation, CA, ClientCertificate, ClientKey, CertificatePassword, username, password, timeout, topicName, Enum_PubOrSub.Subscribe);
		connection.publish(topicName, message, QoS);
	}

	public void deleteClientObject(String brokerHost, Long brokerPort, String username, String topicName, Enum_PubOrSub pubOrSub) throws Exception {

		String key = getKeyForMqttConfig(brokerHost, brokerPort,  username,  topicName,  pubOrSub);
		MqttConnection handler;
		synchronized (mqttHandlers) {

			logger.info("Number of objects in mqttHandlers map before removal: " + mqttHandlers.size());

			if (mqttHandlers.containsKey(key)) {
				logger.info("Removing the connection");
				try {

					handler = mqttHandlers.get(key);

					//Disconnect the Client and Close.
					if(handler.client.isConnected())
						handler.client.disconnect();

					handler.client.close(true);
					mqttHandlers.remove(key);

				} catch (Exception e) {
					logger.error(e);
					throw e;
				}

			} 
			logger.info("Number of objects in mqttHandlers map after removal: " + mqttHandlers.size());
		}
	}

	public static String getKeyForMqttConfig (String brokerHost, Long brokerPort, String username, String topicName, 
			Enum_PubOrSub pubOrSub) {
		return brokerHost + "|" + brokerPort + "|" + username + "|" + topicName + "|" + pubOrSub.getCaption();
	}

	private static MqttConnection getMqttConnection(String brokerHost, Long brokerPort, String brokerOrganisation, String CA, String ClientCertificate,
			String ClientKey, String CertificatePassword, String username, String password, long timeout,
						String topicName, Enum_PubOrSub pubOrSub) throws Exception {
		String key = formatBrokerId(brokerHost, brokerPort, topicName, username);
		MqttConnection handler;
		synchronized (mqttHandlers) {
			logger.trace("Number of active MQTT Connections: " + mqttHandlers.size());

			if (!mqttHandlers.containsKey(key)) {
				logger.info("Creating new MqttConnection to: " + formatBrokerId(brokerHost, brokerPort, topicName, username));

				try {
					handler = new MqttConnection(brokerHost, brokerPort, brokerOrganisation, CA, ClientCertificate, ClientKey, CertificatePassword, username, password, timeout, topicName, pubOrSub);
					mqttHandlers.put(key, handler);
				} catch (Exception e) {
					logger.error("Unable to create an MQTT Connection to: "+ formatBrokerId(brokerHost, brokerPort, topicName, username), e);
					throw e;
				}

			} else {
				logger.info("Found existing MqttConnection for: " + formatBrokerId(brokerHost, brokerPort, topicName, username));
				handler = mqttHandlers.get(key);
			}
			logger.debug("Number of active MQTT Connections: " + mqttHandlers.size());
		}

		return handler;
	}



	protected static class MqttConnection {
		private MqttClient client;
		private HashMap<String, MqttSubscription> subscriptions = new HashMap<>();
		private String brokerKey;
		private MqttConnectOptions connectionOpts;

		public MqttConnection(String brokerHost,  Long brokerPort, String brokerOrganisation, String CA, String ClientCertificate, 
				String ClientKey, String CertificatePassword, String username, String password, long connectionTimeout,
				String topicName, Enum_PubOrSub pubOrSub) throws Exception {
			this.brokerKey = formatBrokerId(brokerHost, brokerPort, topicName, username);

			boolean useSsl = (ClientCertificate != null && !ClientCertificate.equals(""));
			MqttConnectOptions connOpts = new MqttConnectOptions();
			connOpts.setCleanSession(false);
			connOpts.setAutomaticReconnect(true);
			if(connectionTimeout != 0)
				connOpts.setConnectionTimeout(Math.toIntExact(connectionTimeout));
			else
				connOpts.setConnectionTimeout(60);
			connOpts.setKeepAliveInterval(60);

			String brokerURL = "", clientId = "";
			if(brokerOrganisation != null && !brokerOrganisation.equals("")){
				brokerURL = String.format("tcp://%1s.%2s:%d",brokerOrganisation, brokerHost, brokerPort);
			}
			else{
				brokerURL = String.format("tcp://%s:%d", brokerHost, brokerPort);
			}

			String key = getKeyForMqttConfig(brokerHost, brokerPort,  username,  topicName,  pubOrSub);

			clientId = Base64.getEncoder().encodeToString(key.getBytes());
			logger.debug("Assigned MQTT Connection client id " + clientId + " to: " + formatBrokerId(brokerHost, brokerPort, topicName, username));



			if (username != null && !"".equals(username.trim())) {
				connOpts.setUserName(username);
			}
			if (password != null && !"".equals(password.trim())) {
				connOpts.setPassword(password.toCharArray());
			}

			if (useSsl) {
				brokerURL = String.format("ssl://%s:%d", brokerHost, brokerPort);
				connOpts.setCleanSession(false);

				try {
					String resourcesPath = null;
					try {
						resourcesPath = Core.getConfiguration().getResourcesPath().getPath();
						resourcesPath += File.separator;

					} catch (Exception e) {
						resourcesPath = "";
					}
					connOpts.setSocketFactory(SslUtil.getSslSocketFactory(
							resourcesPath + CA,
							resourcesPath + ClientCertificate,
							resourcesPath + ClientKey,
							CertificatePassword
							));
				} catch (Exception e) {
					logger.error(String.format("Unable to load certificates for: " + formatBrokerId(brokerHost, brokerPort, topicName, username), brokerHost,brokerPort), e);
					throw e;
				}
			}

			MemoryPersistence persistence = new MemoryPersistence();

			try {
				this.client = new MqttClient(brokerURL, clientId, persistence);
				this.client.setCallback(new MxMqttCallback(this.brokerKey, this, this.subscriptions, brokerHost, brokerPort));

				logger.debug("Connecting to broker: " + brokerURL);
				IMqttToken token = this.client.connectWithResult(connOpts);
				token.waitForCompletion(connectionTimeout);
				logger.trace("Connected");
			} catch (Exception e) {
				throw e;
			}
		}

		public void subscribe(String topic, String onMessageMicroflow, mqttclient.proxies.qos QoS) throws MqttException {
			logger.info(String.format("Subscribe: %s", this.client.getClientId()));
			try {
				if(!this.client.isConnected()){
					this.client.reconnect();
				}
				int subscriptionQos = 0;
				if(QoS.equals(mqttclient.proxies.qos.At_Most_Once_0)){
					subscriptionQos = 0;
				}else if(QoS.equals(mqttclient.proxies.qos.At_Least_Once_1)){
					subscriptionQos= 1;
				}else if(QoS.equals(mqttclient.proxies.qos.Exactly_Once_2)){
					subscriptionQos= 2;
				}

				/* Request the input parameters from the OnMessageMicroflow so we can 
				 * validate that both 'Topic' & 'Payload' present.
				 */
				Map<String, IDataType> params = Core.getInputParameters(onMessageMicroflow);
				if( !params.containsKey("Topic") && !params.containsKey("Payload") )
					logger.warn("On Message Microflow: " + onMessageMicroflow + " is missing all required parameters [Topic & Payload]");
				else if( !params.containsKey("Topic") )
					logger.warn("On Message Microflow: " + onMessageMicroflow + " is missing parameter [Topic]");
				else if( !params.containsKey("Payload") )
					logger.warn("On Message Microflow: " + onMessageMicroflow + " is missing required parameter [Payload]");


				this.client.subscribe(topic, subscriptionQos);
				this.subscriptions.put(topic, new MqttSubscription(topic, onMessageMicroflow, QoS));
			} catch (Exception e) {
				logger.error(e);
				throw e;
			}

		}
		public void unsubscribe(String topicName) throws MqttException {
			logger.info(String.format("Unsubscribe: %s, %s", topicName, this.client.getClientId()));
			try {
				this.subscriptions.remove(topicName);

				this.client.unsubscribe(topicName);
			} catch (MqttException e) {
				logger.error(e);
				throw e;
			}
			finally {
				if( this.subscriptions.size() == 0 ) { 
					synchronized (mqttHandlers) {
						this.client.disconnect();
						mqttHandlers.remove(this.brokerKey);

						logger.info("Closed MqttConnection after unsubscribing from the last topic. For: " + this.brokerKey);
						logger.debug("Number of active MQTT Connections: " + mqttHandlers.size());
					}
				}
			}
		}

		public void publish(String topic, String message,mqttclient.proxies.qos QoS) throws MqttException {
			logger.debug(String.format("Publish: %s, %s, %s", topic, message, this.client.getClientId()));
			try {
				if(!this.client.isConnected()){
					this.client.reconnect();
				}

				MqttMessage payload = new MqttMessage(message.getBytes());
				int subscriptionQos = 0;
				if(QoS.equals(mqttclient.proxies.qos.At_Most_Once_0)){
					subscriptionQos = 0;
				}else if(QoS.equals(mqttclient.proxies.qos.At_Least_Once_1)){
					subscriptionQos= 1;
				}else if(QoS.equals(mqttclient.proxies.qos.Exactly_Once_2)){
					subscriptionQos= 2;
				}
				payload.setQos(subscriptionQos);
				this.client.publish(topic, payload);

				logger.trace("Message published");
			} catch (Exception e) {
				logger.error("Unable to publish message to topic: " + topic, e);
				throw e;
			}
		}

		public void reconnect() {

			int numAttempts = 0;
			while( numAttempts < 10 && !this.client.isConnected() ) {
				try {
					logger.info(String.format("Attempt (%d/10) to re-establish connection to: %s", numAttempts, this.brokerKey));

					IMqttToken token = this.client.connectWithResult(this.connectionOpts);
					token.waitForCompletion();
					if (this.client.isConnected())
						logger.info(String.format("Attempt (%d/10) - Re-connected to: %s", numAttempts, this.brokerKey));

				} catch (MqttException e) {
					MqttConnector.logger.error(String.format("Attempt (%d/10) - An error occured while reconnecting to: %s", numAttempts, this.brokerKey), e);
				}

				//If we're still not connected wait 2 seconds before trying again
				finally {
					numAttempts++;
					try { 
						if( !this.client.isConnected() ) Thread.sleep(2000);
					} catch(InterruptedException e)  { } ; //Ignore this exception
				}
			}

			if (!this.client.isConnected())
			{
				//logger.error(String.format("Reconnection Failed, quitting after multiple attempts to reconnect to: %s", numAttempts, this.brokerKey));
				try {
					Thread.sleep(60000);
					reconnect();
				} catch (InterruptedException e) {
					logger.error(String.format("Thread Interrupted Exception%n%s", ExceptionUtils.getStackTrace(e)));
				}
			}
		}
	}
	private static String formatBrokerId(String brokerHost, Long brokerPort, String formatBrokerId, String username ) {
		return String.format("[H:%s | P:%d | T:%s | U:%s]", brokerHost, brokerPort, formatBrokerId, username);
	}

	public static IMendixObject checkSubscriptionObj(IContext ctx, String brokerHost, Long brokerPort, String topicName) throws CoreException
	{
		// Retrieving the subscription object 
		String xpathConstraint = "//MqttClient.Subscription" +"[Host = '"+ brokerHost +"' and Port = "+ brokerPort  + " and TopicName = '"+ topicName+ "']" ;
		try {
			List<IMendixObject> subscriptionObjs = com.mendix.core.Core.retrieveXPathQuery(ctx, xpathConstraint);
			if(!subscriptionObjs.isEmpty())
			{
				return subscriptionObjs.get(0);
			}
		} catch (CoreException e) {  
			throw new CoreException("Could not retrieve QueueInfo objects for Instance Index " + xpathConstraint + " from database.", e);
		}
		return null;
	}
}
