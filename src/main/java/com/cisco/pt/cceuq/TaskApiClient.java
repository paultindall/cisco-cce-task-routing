package com.cisco.pt.cceuq;

import com.cisco.thunderhead.client.ContextServiceClient;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

public class TaskApiClient {

    static List<TaskRequester> requesterlist = new ArrayList<>();
    static ContextServiceClient ctxcon;

    public static void main(final String[] args) throws JDOMException, IOException, Exception {

        SAXBuilder sb = new SAXBuilder();
        Document doc = sb.build(new File("taskapiclient_config.xml"));

        Element root = doc.getRootElement();
        String smhost = root.getChildTextTrim("socialMiner");
        
        boolean debug = false;
        String val = root.getChildTextTrim("debug");
        if (val != null) debug = Boolean.parseBoolean(val);
        
// If context service is enabled, establish connection to it

        Element context = root.getChild("contextService");
        
        if (context != null && "true".equals(context.getChildTextTrim("enabled")))
        {
            String hostname = context.getChildTextTrim("hostname");
            String cfgpath = context.getChildTextTrim("propertyFileLocation");
            String key = context.getChildTextTrim("connectionDataFileLocation");
            String mode = context.getChildTextTrim("mode");

            if ((ctxcon = (new CtxSvcConnection(hostname, key, cfgpath)).connect("lab".equals(mode))) == null)
            {
                System.out.println("Context Service connection not established, no PODs will be created");
            }
        }

// Read config for each task requester and create handler in its own thread

        List<Element> reqlist = root.getChildren("requester");
        Iterator<Element> itr = reqlist.iterator();

        while (itr.hasNext()) 
        {
            Element requester = itr.next();

            val = requester.getChildTextTrim("debug");
            boolean reqDebug = val != null ? Boolean.parseBoolean(val) : debug;            
            
            String name = requester.getChildTextTrim("name");
            String smfeed = requester.getChildTextTrim("taskFeed");
            String rdmsghost = requester.getChildTextTrim("readMessageHost");
            String rdmsgq = requester.getChildTextTrim("readMessageQueue");
            String icmdn = requester.getChildTextTrim("scriptSelector");

            if (debug) {
                String label = (name == null) ? "<undefined>" : name;
                System.out.printf("Creating task requester: name = %s, feed = %s, read queue = %s, ICM Script Selector = %s\n", label, smfeed, rdmsgq, icmdn);
            }

            TaskRequester uqrq = new TaskRequester(smhost, smfeed, icmdn, rdmsghost, rdmsgq);
            if (name != null) uqrq.setName(name);
            uqrq.setDebug(reqDebug);

// Add variables and tags

            requester.getChild("taskData").getChildren().forEach(e -> {
                String k = e.getName();
                String v = e.getTextTrim();

                if (k.equals("tag")) {
                    uqrq.addTag(v);
                } else {
                    uqrq.addVariable(k, v);
                }
            });

// Set Context Service parameters if it's enabled for this requester

            val = requester.getChildTextTrim("createPod");
            boolean useCtx = !(ctxcon == null || (val != null && !Boolean.parseBoolean(val)));            
            uqrq.useContextService(useCtx);

            if (useCtx)
            {
                requester.getChild("customerLookup").getChildren().forEach(e -> {
                    uqrq.addContextSearchField(e.getName(), e.getTextTrim());
                });

                requester.getChild("podData").getChildren().forEach(e -> {
                    uqrq.addContextPodField(e.getName(), e.getTextTrim());
                });
            }

            uqrq.start();

            requesterlist.add(uqrq);
        }
    }
}
