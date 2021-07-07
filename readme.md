# Mendix MQTT connector

Mendix module to send and receive [MQTT][1] messages. This module uses the [Eclipse Paho][3] library. 
Tested with [mosquitto][2] broker.

## Configuration

- Download the latest 'MqttClient' module into your project.

- Map the module role 'Configurator' to the applicable user roles in your application.

- Add 'OSu_MqttStartup' microflow to your on start up microflow.

  (This microflow is to retrieve all the broker configurations from the database and subscribe on app startup.)

- Add 'Snip_MqttOverview' snippet to a custom page (Or use existing 'Mqtt_Overview' page) and Add your page to navigation.

- Added page will be visible as shown below.

  

  ![Mqtt Overview][26]

  Now you need to configure below configurations :

  1. Broker Configuration

  2. Mqtt Configuration

  ##### Broker Configuration:

  ![Broker Configuration][19]

  

  ![Broker Configuration cont][20]

  Parameters (if parameters are optional you can pass empty or ''):

  * **Configuration name** *(required)*: Unique name for your configuration. (e.g. 'mosquitto broker')

  * **Broker host** *(required)*: The url of your MQTT broker, needs to be the URL without protocol and without port. (e.g. 'test.mosquitto.org')

  * **Broker port** *(required)*: The port of your broker 

  * **Broker organization** *(optional)*: Some brokers use an organization as part of the URL, pass that here.

  * **Timeout** *(required)*: Maximum timeout between broker and this application in Seconds (default=60)

  * **Username** *(optional)*: If your broker has username/password authentication pass the username here. If you set a username you must set a password. 

  * **Password** *(optional)*: If your broker has username/password authentication pass the username here. If you set a password you must set a username.

  * **CA** *(optional)*: If the broker requires a (custom) SSL authentication pass the certificates here. This is the Certificate Authority used for the connection. Place the certificates inside of the resource folders. This value must be a relative path inside the resource folder. E.g.:   'cert/ca.pem' if your certificate is located in 'resources/cert/'.  

  * **Client certificate** *(optional)*: If the broker requires a (custom) SSL authentication pass the certificates here. This is the Client Certificate used for the connection. Place the certificates inside of the resource folders. This value must be a relative path inside the resource folder. E.g.:   'cert/client.pem' if your certificate is located in 'resources/cert/'.

  * **Client key** *(optional)*: If the broker requires a (custom) SSL authentication pass the certificates here. This is the Client Key used for the connection. Place the certificates inside of the resource folders. This value must be a relative path inside the resource folder. E.g.:   'cert/client.key' if your certificate is located in 'resources/cert/'.

  * **Certificate Password** *(optional)*: If the broker requires a (custom) SSL authentication pass the certificates here. This is password that is needed to use the client key. 

  * **Clean session** : If the app is down and up again -

    If the clean session is 'Yes', You will not receive the messages has been published when the app is down. If the clean session is 'No', You will receive all the messages has been published to the app when the app is down.

  ##### Mqtt Configuration:

  ![Mqtt Configuration][21]

  

  * **Configuration name** *(required)*: Unique name for your configuration. (e.g. 'Subscribe Configuration')
  * **Publish or Subscribe** : Select 'Subscribe', If you are creating subscribe configuration.
  * **Topic name** *(required)*: The full topic name you want to publish the message to. This needs to be the topic name starting at the root, but **not** including a leading slash. Example: 'iot-2/type/exampledevice/status'
  * **Subscribe** : Select 'Yes', If you wants to subscribe.
  * **QOS** *(required)*: Passes the Quality of Service parameter straight to the broker (At_Most_Once_0 / At_Least_Once_1 / Exactly_Once_2). *See MQTT service definitions for more info, i.e.: [https://mosquitto.org/man/mqtt-7.html#idm72][17]* (In short, Higher levels of QoS are more reliable, but involve higher latency and have higher bandwidth requirements.)
  * **Broker Configuration** : Select a broker configuration.

## Usage

Main java actions:

 * MqttPublish - publish a message to specified topic
 * MqttSubscribe - subscribe to a topic. Required you to specify a microflow which will be called upon receiving
   a message. This microflow should have two string parameters: Topic and Payload.
 * MqttUnsubscribe - unsubscribe from topic

  ![MQTT Microflow actions toolbox][9]


### MQTT Publish
This activity allows you to publish a message to a specific MQTT topic. When you execute this activity the module will setup a connection to the topic and publish the message, it will retain the connection in memory for faster publishing in the future.  

![Mqtt Publish][25]

* **Mqtt config object** : Provide a Mqtt configuration object.
* **Payload** : Provide a message to be published. 


### MQTT Subscribe
This activity sets up the Subscription to an MQTT topic. When you execute this activity the module will setup a connection to the topic and wait for any message that comes in on the topic.  

Microflow to subscribe to an MQTT topic:

 ![MQTT subscribe to topic][10]

Configuration of subscribe :

![Mqtt subscribe][22]

* **Mqtt config object** : Provide a Mqtt configuration object.
* **On message microflow** : Provide a new microflow to process the arrived message or use 'ACT_ProcessPayloadMessage' microflow and extend it as needed.

Microflow to handled messages received:

![On message microflow][23]

### MQTT Unsubscribe
This activity will unsubscribe your application from listening to an MQTT topic. 

![Mqtt Unsubscribe][24]


* **Mqtt config object** : Provide a Mqtt configuration object.

### Development

Java dependencies are managed using Apache Ivy. There are two configuration:
* export - this is used to make sure only the required jars for the connector module are in userlib
* default - this downloads all dependencies required to run the project

Before you export the connector module run runivy-export.cmd to ensure you have the correct set of libraries to be
included in the connector mpk.

## License

 [Apache License V2.0][13]

 [Eclipse Public License][18]

[1]: http://mqtt.org/
[2]: http://mosquitto.org/
[3]: http://www.eclipse.org/paho/
[4]: http://thethingsnetwork.org/
[5]: https://aws.amazon.com/iot/
[6]: https://staging.thethingsnetwork.org/wiki/Backend/Connect/Application
[7]: https://staging.thethingsnetwork.org/wiki/Backend/Security
[8]: https://staging.thethingsnetwork.org/wiki/Backend/ttnctl/QuickStart
[9]: docs/images/mqtt-toolbox.png
[10]: docs/images/ttn-subscribe.png
[11]: docs/images/ttn-subscribe-details.png
[12]: docs/images/ttn-callback-microflow.png
[13]: license.txt
[14]: docs/blogpost-ttn-mqtt-mendix.md
[15]: docs/images/IBM.png
[16]: docs/images/IBMApps.png
[17]: https://mosquitto.org/man/mqtt-7.html#idm72
[18]: https://www.eclipse.org/legal/epl-2.0/
[19]: /docs/images/Broker_Configuration.PNG
[20]: docs/images/Broker_Configuration2.PNG
[21]: docs/images/Mqtt_Configuration.PNG
[22]: docs/images/Mqtt_Subscribe.PNG
[23]: docs/images/ProcessPayloadMessage_Microflow.PNG
[24]: docs/images/Mqtt_Unsubscribe.PNG
[25]: docs/images/Mqtt_Publish.PNG
[26]: docs/images/Mqtt_Overview.PNG



