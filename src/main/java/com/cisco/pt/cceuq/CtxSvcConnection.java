package com.cisco.pt.cceuq;

import com.cisco.thunderhead.client.ContextServiceClient;
import com.cisco.thunderhead.connector.ConnectorConfiguration;
import com.cisco.thunderhead.connector.info.ConnectorInfo;
import com.cisco.thunderhead.connector.info.ConnectorInfoImpl;
import com.cisco.thunderhead.plugin.ConnectorFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


public class CtxSvcConnection {

    private final String connectionHostname;
    private final String connectionDataFilename;
    private final String configPath;

    public CtxSvcConnection(String connectHost, String connectData, String configPath) {

        this.connectionHostname = connectHost;
        this.connectionDataFilename = connectData;
        this.configPath = configPath;
    }


    ContextServiceClient connect(boolean labMode) {

        ContextServiceClient ctxClient = null;

        ConnectorConfiguration config = new ConnectorConfiguration();
        config.addProperty("LAB_MODE", labMode);

        ConnectorFactory.initializeFactory(Paths.get(configPath).toAbsolutePath().toString());
        ConnectorInfo connInfo = new ConnectorInfoImpl(connectionHostname);

        try {
            String key = new String(Files.readAllBytes(Paths.get(connectionDataFilename)));

            ctxClient = ConnectorFactory.getConnector(ContextServiceClient.class);        
            ctxClient.init(key, connInfo, config);

            System.out.println("\n *** Client connector connected (lab mode = " + labMode + ")\n");

        } catch (IOException ex) {
            System.out.println("\n *** Client connection error, " + ex);

            if (ctxClient != null) {
                ctxClient.destroy();
                ctxClient = null;
            }            
        }

        return ctxClient;
    }    
}
