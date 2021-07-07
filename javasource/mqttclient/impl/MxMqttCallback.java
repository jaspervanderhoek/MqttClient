package mqttclient.impl;

import java.util.List;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.systemwideinterfaces.MendixRuntimeException;

import mqttclient.impl.MqttConnector.MqttConnection;
import mqttclient.proxies.BrokerConfig;
import mqttclient.proxies.Enum_PubOrSub;
import mqttclient.proxies.MqttConfig;
import mqttclient.proxies.Subscription;
import objecthandling.XPath;

/**
 * Created by ako on 12-8-2016.
 */
public class MxMqttCallback implements MqttCallbackExtended {
    private MqttConnection mqttConnection = null;
    private BrokerConfig brokerConfig;
    
    protected MxMqttCallback(String brokerKey, MqttConnection mqttConnection, BrokerConfig brokerConfig) {
        this.mqttConnection = mqttConnection;
        this.brokerConfig = brokerConfig;
    }

    @Override
    public void connectionLost(Throwable throwable) {
        MqttConnector.logger.warn(String.format("Connection Lost for: %s | %s", MqttConnector.getKeyForBrokerConfig(brokerConfig), 
                throwable.getMessage()), throwable);
    	try {
            this.mqttConnection.reconnect();
        } catch (CoreException e) {
            MqttConnector.logger.warn(String.format("Exception encountered during reconnect: %s", e.getMessage()));
            throw new MendixRuntimeException(e);
        }		
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        try {
            MqttConnector.logger.info(String.format("Message Arrived for: %s%nMessage:%n%s", MqttConnector.getKeyForBrokerConfig(brokerConfig), 
                    new String(mqttMessage.getPayload())));
           
            // Getting subscription object from mqtt configuration
         	Subscription subscription = getSubscriptionForTopic(topic);
         	
            if (subscription != null) {
                String microflow = subscription.getOnMessageMicroflow();
                MqttConnector.logger.trace(String.format("Calling onMessage microflow: %s, %s", microflow, 
                        MqttConnector.getKeyForBrokerConfig(brokerConfig)));
                
                Core.microflowCall(microflow)
                //  check what are all needed to send to mf or we can send mqttconfig
	               // .withParam("Host", subscription.getHost())
	               // .withParam("Port", subscription.getPort())
                	.withParam("Topic", topic)
                	.withParam("Payload", new String(mqttMessage.getPayload()))
                	.execute(Core.createSystemContext());
            } else {
                MqttConnector.logger.error(String.format("Cannot find microflow for message received on topic %s", topic));
            }
        } catch (Exception e) {
            MqttConnector.logger.error(e);
        }
    }
    
    /**
     * Retrieve Subscription object by Getting all the MQTT configurations 
     * with the subscription for the topic configured and enabled.
     * 
     * @param topic
     * @return
     * @throws CoreException 
     */
    private Subscription getSubscriptionForTopic(String topic) throws CoreException {
        
        List<MqttConfig> mqttConfigList =  XPath.create(this.brokerConfig.getContext(), MqttConfig.class)
                        .subconstraint(MqttConfig.MemberNames.MqttConfig_BrokerConfig, mqttclient.proxies.BrokerConfig.entityName)
                            .eq(XPath.ID, this.brokerConfig.getMendixObject().getId().toLong())
                        .close()
                        .eq(MqttConfig.MemberNames.PubOrSub, Enum_PubOrSub.Subscribe)
                        .and()
                        .eq(MqttConfig.MemberNames.SubOrUnSub, true)
                        .all();
                
        
        for(MqttConfig mqttCfg: mqttConfigList) {
            
            String topicWithWildcards = mqttCfg.getTopicName();
            String topicWithWildcardsRe = topicWithWildcards.replaceAll("\\+", "[^/]+").replaceAll("/#", "\\(|/.*\\)");
            MqttConnector.logger.info(String.format("Comparing topic %s with subscription %s as regex %s", 
                    topic, topicWithWildcards, topicWithWildcardsRe));
            if (topic.matches(topicWithWildcardsRe)) {
                MqttConnector.logger.info("Found subscription " + topicWithWildcards);
                return mqttCfg.getMqttConfig_Subscription();
            }
        }        
        
        return null;
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        MqttConnector.logger.info(String.format("deliveryComplete: %s", MqttConnector.getKeyForBrokerConfig(brokerConfig)));
    }

    @Override
    public void connectComplete(boolean isReconnect, String serverUri) {
        MqttConnector.logger.info(String.format("connectComplete Method: %s, %s", isReconnect, serverUri));
    }
}