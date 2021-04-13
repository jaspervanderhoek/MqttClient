package mqttclient.impl;

import java.util.HashMap;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import com.mendix.core.Core;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import mqttclient.impl.MqttConnector.MqttConnection;
import mqttclient.proxies.Subscription;

/**
 * Created by ako on 12-8-2016.
 */
public class MxMqttCallback implements MqttCallbackExtended {
	private MqttConnection mqttConnection = null;
	private String brokerKey;
	private String brokerHost;
	private Long brokerPort;

	protected MxMqttCallback(String brokerKey, MqttConnection mqttConnection, HashMap<String, MqttSubscription> subscriptions, String brokerHost, Long brokerPort) {
		this.mqttConnection = mqttConnection;
		this.brokerKey=brokerKey;
		this.brokerHost = brokerHost;
		this.brokerPort = brokerPort;
	}

	@Override
	public void connectionLost(Throwable throwable) {
		MqttConnector.logger.warn(String.format("Connection Lost for: %s | %s", this.brokerKey, throwable.getMessage()), throwable);
		this.mqttConnection.reconnect();		
	}

	@Override
	public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
		try {
			MqttConnector.logger.debug(String.format("Message Arrived for: %s | %s", this.brokerKey, new String(mqttMessage.getPayload())));
			
			IContext ctx = Core.createSystemContext();
			IMendixObject subscriptionObjAvailable = MqttConnector.checkSubscriptionObj(ctx, this.brokerHost, this.brokerPort, topic);
			if(subscriptionObjAvailable != null)
			{
				Subscription subscriptionObj = mqttclient.proxies.Subscription.initialize(ctx, subscriptionObjAvailable);
				MqttConnector.logger.info(String.format("Calling onMessage microflow - %s", subscriptionObj.getOnMessageMicroflow()));
				executeOnMessageMicroflow(ctx, subscriptionObj.getOnMessageMicroflow(), subscriptionObj.getHost(), subscriptionObj.getPort(), topic, mqttMessage);
			}
			else 
				MqttConnector.logger.error(String.format("Cannot find microflow for message received on topic %s", topic));
			
		} catch (Exception e) {
			MqttConnector.logger.error(e);
		}
	}

	public void executeOnMessageMicroflow (IContext ctx, String microflow, String brokerHost, Long brokerPort, String topic, MqttMessage mqttMessage)
	{
		Core.microflowCall(microflow)
		.withParam("Host", brokerHost )
		.withParam("Port", brokerPort)
		.withParam("Topic", topic)
		.withParam("Payload", new String(mqttMessage.getPayload()))
		.execute(ctx);
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
		MqttConnector.logger.info(String.format("deliveryComplete: %s", this.brokerKey));
	}

	@Override
	public void connectComplete(boolean isReconnect, String serverUri) {
		/*MqttConnector.logger.info(String.format("connectComplete %s, %s", isReconnect, serverUri));
		this.subscriptions.forEach((topic, subs) -> {
			try {
				MqttConnector.logger.info(String.format("Resubscribing microflow %s to topic %s (%s)", subs.getOnMessageMicroflow(), topic, subs.getTopic()));
				this.mqttConnection.subscribe(topic, subs.getOnMessageMicroflow(),subs.getQoS());
			} catch (MqttException e) {
				MqttConnector.logger.error(String.format("Reconnect failed for topic %s: %s", topic, e.getMessage()));
			}
		});*/
	}
}
