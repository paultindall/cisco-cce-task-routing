# cisco-cce-task-routing

This is a generic CCE Task Routing client that takes incoming JSON format requests from multiple sources and generates task routing requests to SocialMiner.

_What It Does_

 - Reads input using long poll on any number of different hosts
 - Connects out rather than listens for incoming connections to make it friendlier for use in internal labs when getting event triggers from the public internet. This does mean you have to implement a cloud message queue or equivalent to read from. It currently reads from http://\<hostname\>/readmessage/\<queuename\> using the host and queue name fields specified for the task sources in the config file.
- If you want to have it listen for incoming messages then the simplest thing would be to rework it slightly as a web app.
- Processes incoming requests in JSON format
- Using a configuration file, extracts incoming JSON data items and maps them to task request fields
- Issues task requests to SocialMiner
- Optionally, includes Context Service integration to retrieve customer info and create a new CS POD
- Merges CS customer data block into the incoming request JSON so CS data can be included in the task request.

_Building_

Before building load the context service dependencies into your local repository. Navigate to the directory containing pom.xml and execute the command:

mvn -U install:install-file -Dfile=src/main/resources/context-service-sdk-2.0.1.jar -DgroupId=com.cisco.thunderhead -DartifactId=context-service-sdk -Dversion=2.0.1 -Dpackaging=jar -DpomFile=src/main/resources/context-service-sdk-pom.xml

Build using mvn install


_Configure And Run_

For Context Service integration, set the location of connection.data (containing access key from the CS registration process) and connector.property (containing the CS plugin location) in taskapiclient_config.xml.

Add the SocialMiner host IP/name and descriptors for each input source you want to read requests from.
Configure the mapping of incoming JSON content to outgoing task request fields; this should be self explanatory from the example config file.

Run with java -jar cce_uq_client-1.0.jar

