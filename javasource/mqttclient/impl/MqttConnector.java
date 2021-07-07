package mqttclient.impl;

import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.google.common.base.Strings;
import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.logging.ILogNode;
import com.mendix.systemwideinterfaces.MendixRuntimeException;
import com.mendix.systemwideinterfaces.core.IDataType;


import mqttclient.proxies.BrokerConfig;
import mqttclient.proxies.Enum_PubOrSub;
import mqttclient.proxies.MqttConfig;
import mqttclient.proxies.qos;
import objecthandling.XPath;

/**
 * Created by ako on 1/9/2016.
 */
public class MqttConnector {
    private static Map<String, MqttConnection> mqttHandlers = new HashMap<>();
    public static ILogNode logger = Core.getLogger("MqttConnector");
    //private static MqttConfig mqttConfig;

    private MqttConnector() { }


    public static void subscribe(MqttConfig mqttConfig, BrokerConfig brokerConfig, String onMessageMicroflow) throws Exception {
        MqttConnection connection = getMqttConnection(brokerConfig);
        connection.subscribe(mqttConfig.getTopicName(), onMessageMicroflow, mqttConfig.getQos());
    }

    public static void unsubscribe(MqttConfig mqttConfig, BrokerConfig brokerConfig) throws Exception {
        MqttConnection connection = getMqttConnection(brokerConfig);
        connection.unsubscribe(mqttConfig.getTopicName());
    }

    public static void publish(MqttConfig mqttConfig, BrokerConfig brokerConfig, String payload) throws Exception {
        MqttConnection connection = getMqttConnection(brokerConfig);
        connection.publish(mqttConfig.getTopicName(), payload, mqttConfig.getQos());
    }

    public static void deleteConnection(BrokerConfig brokerConfig) throws Exception {

        String key = getKeyForBrokerConfig(brokerConfig);
        MqttConnection handler = null;

        synchronized (mqttHandlers) {

            MqttConnector.logger.info("Number of objects in mqttHandlers map before removal: " + mqttHandlers.size());

            if (mqttHandlers.containsKey(key)) {
                MqttConnector.logger.info("Removing the connection");
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

    public static MqttConnection getMqttConnection(BrokerConfig brokerConfig) throws Exception {


        String key = getKeyForBrokerConfig(brokerConfig);
        String exceptionMsg = String.format("Unable to fetch connection object for the key: %s", key);

        synchronized (mqttHandlers) {
            MqttConnector.logger.trace("Number of active MQTT Connections: " + mqttHandlers.size());

            if(mqttHandlers.containsKey(key))
                return mqttHandlers.get(key);

            //We should not recreate connection object here, instead throw an exception since,
            //creation of connection object happens during broker configuration. If there is no
            //connection object at this point, then there could be an unhandled bug which needs to be
            //fixed.
            MqttConnector.logger.error(exceptionMsg);
            throw new MendixRuntimeException(exceptionMsg);
        }

    }


    private static MqttConnection createConnection(BrokerConfig brokerConfig) throws Exception {

        MqttConnection handler = null;
        String key = getKeyForBrokerConfig(brokerConfig);

        try {
            handler = new MqttConnection(brokerConfig);
            synchronized (mqttHandlers) {
                mqttHandlers.put(key, handler);
            }

        } catch (Exception e) {
            MqttConnector.logger.error("Unable to create an MQTT Connection to: "+ key, e);
            throw e;
        }

        return handler;
    }

    public static MqttConnection validateAndcreateConnection(BrokerConfig brokerConfig) throws Exception {

        String key = getKeyForBrokerConfig(brokerConfig);

        //We always delete the existing connection object if existing, recreate the same
        //and resubscribe to the required topics. This is because the save action from the
        //UI indicates that there could be possible configuration change.
        //For Example: Password to connect to the broker is modified.
        synchronized (mqttHandlers) {

            if(!mqttHandlers.containsKey(key))
                return createConnection(brokerConfig);


            // Deleting the existing connection to recreate the connection object.
            // This is done to handle updates on broker configuration object.
            // For example: Password change.
            deleteConnection(brokerConfig);

            // Creating new connection
            MqttConnection mqttConnection = createConnection(brokerConfig);

            //Resubscribe to all topics that connection holds responsible to 
            //since the existing connection was removed and re-established.
            resubscribe(brokerConfig, mqttConnection);

            return mqttConnection;
        }

    }

    //This method is responsible to initialize connections and subscribe to configured topics on
    //app startup.
    public static MqttConnection initializeConnectionOnStartup(BrokerConfig brokerConfig) throws Exception {

        String key = getKeyForBrokerConfig(brokerConfig);
        MqttConnector.logger.info(String.format("Initializing connection on startup for the broker: %s", key));

        MqttConnection mqttConnection = createConnection(brokerConfig);

        //Resubscribe to all configured topics.
        resubscribe(brokerConfig, mqttConnection);

        return mqttConnection;

    }

    public static String getKeyForBrokerConfig (BrokerConfig brokerConfig) {
        
        String brokerOrg = Strings.isNullOrEmpty(brokerConfig.getBrokerOrganization()) ? "null" 
                    : brokerConfig.getBrokerOrganization();
        String username = Strings.isNullOrEmpty(brokerConfig.getUserName()) ? "null" 
                    : brokerConfig.getUserName();
        
        return brokerConfig.getConfigurationName() + "|" + brokerConfig.getBrokerHost() + "|" + 
            brokerConfig.getBrokerPort() + "|" + brokerOrg + "|" + username ;
    }

    protected static class MqttConnection {
        private MqttClient client;
        private String brokerKey;
        private MqttConnectOptions connOpts;
        private BrokerConfig brokerConfig;

        public MqttConnection(BrokerConfig brokerConfig) throws Exception {

            this.brokerConfig = brokerConfig;
            this.connOpts = new MqttConnectOptions();

            String clientCertificate = brokerConfig.getClientCertificate();
            String brokerOrganization = brokerConfig.getBrokerOrganization();
            String brokerHost = brokerConfig.getBrokerHost();
            Long brokerPort = brokerConfig.getBrokerPort();
            String userName = brokerConfig.getUserName();
            String password = brokerConfig.getPassword();
            Long connectionTimeout = brokerConfig.getTimeout();
            String clientKey = brokerConfig.getClientKey();
            String certificatePassword = brokerConfig.getCertificatePassword();
            String ca = brokerConfig.getCA();
            boolean cleanSession = brokerConfig.getCleanSession();


            boolean useSsl = (clientCertificate != null && !clientCertificate.equals(""));
            this.connOpts.setCleanSession(cleanSession);
            this.connOpts.setAutomaticReconnect(true);
            if(connectionTimeout != 0)
                this.connOpts.setConnectionTimeout(Math.toIntExact(connectionTimeout));
            else
                this.connOpts.setConnectionTimeout(60);
            this.connOpts.setKeepAliveInterval(60);

            String brokerURL = "";
            String clientId = "";
            if(brokerOrganization != null && !brokerOrganization.equals("")){
                brokerURL = String.format("tcp://%1s.%2s:%d",brokerOrganization, brokerHost, brokerPort);
            }
            else{
                brokerURL = String.format("tcp://%s:%d", brokerHost, brokerPort);
            }

            this.brokerKey = getKeyForBrokerConfig(brokerConfig);

            clientId = Base64.getEncoder().encodeToString(this.brokerKey.getBytes());
            logger.debug("Assigned MQTT Connection client id : " + clientId + " to: " + this.brokerKey);

            if (userName != null && !"".equals(userName.trim())) {
                this.connOpts.setUserName(userName);
            }
            if (password != null && !"".equals(password.trim())) {
                this.connOpts.setPassword(password.toCharArray());
            }

            if (useSsl) {
                brokerURL = String.format("ssl://%s:%d", brokerHost, brokerPort);
                this.connOpts.setCleanSession(cleanSession);

                try {
                    String resourcesPath = null;
                    try {
                        resourcesPath = Core.getConfiguration().getResourcesPath().getPath();
                        resourcesPath += File.separator;

                    } catch (Exception e) {
                        resourcesPath = "";
                    }
                    this.connOpts.setSocketFactory(SslUtil.getSslSocketFactory(
                            resourcesPath + ca,
                            resourcesPath + clientCertificate,
                            resourcesPath + clientKey,
                            certificatePassword
                            ));
                } catch (Exception e) {
                    logger.error(String.format("Unable to load certificates for: " + this.brokerKey), e);
                    throw e;
                }
            }

            MemoryPersistence persistence = new MemoryPersistence();

            try {
                this.client = new MqttClient(brokerURL, clientId, persistence);
                this.client.setCallback(new MxMqttCallback(this.brokerKey, this, brokerConfig));

                logger.debug("Connecting to broker: " + brokerURL);
                IMqttToken token = this.client.connectWithResult(this.connOpts);
                token.waitForCompletion(connectionTimeout);
                logger.trace("Connected");
            } catch (Exception e) {
                throw e;
            }
        }

        public void subscribe(String topic, String onMessageMicroflow, qos qos) throws MqttException {
            logger.info(String.format("Subscribe: %s", this.client.getClientId()));
            String onMessage = "On Message Microflow: ";
            try {
                if(!this.client.isConnected()){
                    this.client.reconnect();
                }

                /* Request the input parameters from the OnMessageMicroflow so we can 
                 * validate that both 'Topic' & 'Payload' present.
                 */
                Map<String, IDataType> params = Core.getInputParameters(onMessageMicroflow);
                if( !params.containsKey("Topic") && !params.containsKey("Payload") )
                    logger.warn(onMessage + onMessageMicroflow + " is missing all required parameters [Topic & Payload]");
                else if( !params.containsKey("Topic") )
                    logger.warn(onMessage + onMessageMicroflow + " is missing parameter [Topic]");
                else if( !params.containsKey("Payload") )
                    logger.warn(onMessage + onMessageMicroflow + " is missing required parameter [Payload]");

                this.client.subscribe(topic, qosValue(qos));
            } catch (Exception e) {
                logger.error(e);
                throw e;
            }

        }

        public void unsubscribe(String topicName) throws MqttException {
            logger.info(String.format("Unsubscribe: %s, %s", topicName, this.client.getClientId()));
            try {
                this.client.unsubscribe(topicName);
            } catch (MqttException e) {
                logger.error(e);
                throw e;
            }
        }

        public void publish(String topic, String message, qos QoS) throws MqttException {
            logger.debug(String.format("Publish: %s, %s, %s", topic, message, this.client.getClientId()));
            try {
                if(!this.client.isConnected()){
                    this.client.reconnect();
                }

                MqttMessage payload = new MqttMessage(message.getBytes());
                payload.setQos(qosValue(QoS));
                this.client.publish(topic, payload);

                logger.trace("Message published");
            } catch (Exception e) {
                logger.error("Unable to publish message to topic: " + topic, e);
                throw e;
            }
        }

        public void reconnect() throws CoreException {

            int numAttempts = 0;
            String key = getKeyForBrokerConfig(this.brokerConfig);
            while( numAttempts < 10 && !this.client.isConnected() && canEstabilishReconnection(key)) {
                try {
                    MqttConnector.logger.info(String.format("Attempt (%d/10) to re-establish connection to: %s", numAttempts, this.brokerKey));

                    IMqttToken token = this.client.connectWithResult(this.connOpts);
                    token.waitForCompletion();
                    if (this.client.isConnected())
                        MqttConnector.logger.info(String.format("Attempt (%d/10) - Re-connected to: %s", numAttempts, this.brokerKey));

                    //resubscribe to required topics if available.
                    MqttConnector.resubscribe(this.brokerConfig, this);

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
                MqttConnector.logger.error(String.format("Reconnection Failed, quitting after multiple attempts to reconnect to: %s", numAttempts, this.brokerKey));
        }

        public MqttClient getClient() {
            return this.client;
        }

        private boolean canEstabilishReconnection(String clientKey) {

            /**
             * There can be a possibility where a broker config can be removed from the configuration. In such cases, 
             * we need not estabilish reconnection. Let us validate for the configuration existence before establishing
             * the reconnection.
             * 
             * When we delete broker config, we disconnect the client and remove from the map from the method
             * deleteConnection
             */
            synchronized (MqttConnector.mqttHandlers) {
                if(MqttConnector.mqttHandlers.containsKey(clientKey))
                    return true;
            }

            return false;
        }
    }

    /**
     * Get all MQTT configurations with subscribe type and subscription enabled from the
     * broker config object.
     * Resubscribe to those topics with this client.
     * 
     * @param mqttConnection 
     * @param brokerConfig 
     * @throws CoreException 
     */
    public static void resubscribe(BrokerConfig brokerConfig, MqttConnection mqttConnection) throws CoreException {

        List<MqttConfig> mqttCfgList = getMqttCfgList(brokerConfig);

        mqttCfgList.forEach(mqttCfg -> {
            try {
                mqttConnection.getClient().subscribe(mqttCfg.getTopicName(), qosValue(mqttCfg.getQos()));
                logger.info("Resubscribed to : " + mqttCfg.getTopicName());
            } catch (MqttException e) {
                MqttConnector.logger.error(String.format("Resubscription failed for the configuration: %s, Topic:%s", 
                        mqttCfg.getConfigurationName(), mqttCfg.getTopicName()));
            }
        });
    }

    private static List<MqttConfig> getMqttCfgList(BrokerConfig brokerConfig) throws CoreException {

        return XPath.create(brokerConfig.getContext(), MqttConfig.class)
                .subconstraint(MqttConfig.MemberNames.MqttConfig_BrokerConfig, mqttclient.proxies.BrokerConfig.entityName)
                .eq(XPath.ID, brokerConfig.getMendixObject().getId().toLong())
                .close()
                .eq(MqttConfig.MemberNames.PubOrSub, Enum_PubOrSub.Subscribe)
                .and()
                .eq(MqttConfig.MemberNames.SubOrUnSub, true)
                .all();


    }

    private static int qosValue(qos qoS)
    {
        int subscriptionQos = 0;
        if(qoS.equals(mqttclient.proxies.qos.At_Most_Once_0)){
            subscriptionQos = 0;
        }else if(qoS.equals(mqttclient.proxies.qos.At_Least_Once_1)){
            subscriptionQos= 1;
        }else if(qoS.equals(mqttclient.proxies.qos.Exactly_Once_2)){
            subscriptionQos = 2;
        }
        return subscriptionQos;
    }



}