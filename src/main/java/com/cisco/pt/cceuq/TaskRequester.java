package com.cisco.pt.cceuq;

import com.cisco.thunderhead.client.SearchParameters;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import org.json.JSONException;
import org.json.JSONObject;

public class TaskRequester extends Thread {

    private final String smhost, smfeed, icmdn, relayhost, readqueue;
    private final Client client;
    private boolean debug = false;
    private boolean usectx = false;
    private final List<String> taglist;
    private final Map<String, String> varmap;
    private final Map<String, String> ctxpodmap;
    private final Map<String, String> ctxsrchmap;

    public TaskRequester(String smhost, String smfeed, String icmdn, String relayhost, String readqueue) {

        this.smhost = smhost;
        this.smfeed = smfeed;
        this.icmdn = icmdn;
        this.relayhost = relayhost;
        this.readqueue = readqueue;

        taglist = new ArrayList<>();
        varmap = new HashMap();
        ctxsrchmap = new HashMap();
        ctxpodmap = new HashMap();

        client = ClientBuilder.newBuilder().build();
    }

    @Override
    public void run() {
        
        while (true) {

            Response rsp = null;
            int waitsecs = 0;

            try {
                rsp = client.target(relayhost)
                            .path("readmessage")
                            .path(readqueue)
                            .queryParam("wait", 60)
                            .request()
                            .get();

                Response.Status.Family family = rsp.getStatusInfo().getFamily();

                if (Response.Status.Family.SUCCESSFUL == family) {

                    String json = rsp.readEntity(String.class).trim();

                    if (json.isEmpty()) {
                        log("Long-poll timeout");

                    } else {

                        try {
                            log("\n" + (new JSONObject(json)).toString(4));
                            
                            String podURL = null;
                            String cusjson = "{}";

                            if (usectx) {
                                try {
                                    CustomerContext ctx = new CustomerContext();

                                    SearchParameters msrch = new SearchParameters();
                                    ctxsrchmap.forEach((k, v) -> {msrch.add(k, substituteJsonFields(v, json)); });
                                    cusjson = ctx.getCustomer(msrch);

                                    Map<String, String> mdata = new HashMap();
                                    ctxpodmap.forEach((k, v) -> {mdata.put(k, substituteJsonFields(v, json)); });
                                    podURL = ctx.addPod(mdata);

                                } catch (Exception ex) {
                                    log(ex.getMessage());
                                }
                            }
                            
                            String subjson = json.substring(0, json.length() - 1).concat(",\"context\":").concat(cusjson).concat("}");
                            log("\n" + (new JSONObject(subjson)).toString(4));

                            try (AgentUQueueRequest agreq = new AgentUQueueRequest(smhost, smfeed, icmdn)) {

                                varmap.forEach((k, v) -> {
                                    v = substituteJsonFields(v, subjson);
                                    
                                    if (k.equals("title")) {
                                        agreq.setReqTitle(v);

                                    } else if (k.equals("name")) {
                                        agreq.setReqName(v);
                                        
                                    } else if (k.equals("description")) {
                                        agreq.setReqDescrip(v);
                                        
                                    } else if (k.startsWith("cv_")) {
                                        agreq.setCv(k, v);

                                    } else if (k.startsWith("user.")) {
                                        agreq.setEcc(k, v);

                                    } else {
                                        agreq.setVar(k, v);
                                    }
                                });
                                
                                if (podURL != null)
                                    agreq.setVar("podRefURL", podURL);

                                taglist.forEach(t -> {
                                    t = substituteJsonFields(t, json);                                   
                                    agreq.setTag(t);
                                });

                                if (agreq.create() != null) {
                                    log("Agent Request Status: " + agreq.statusReason + " / " + agreq.status() + " (" + agreq.taskStatusReason + ", ID = " + agreq.taskId + ", wait time = " + agreq.taskEstWaitTime + ")");                                        

                                } else {
                                    log("Agent Request Failed: " + agreq.statusReason + " / " + agreq.errorReason);                                        
                                }
                            }

                        } catch (JSONException ex) {
                            ex.printStackTrace();
                        }
                    }

                } else {
                    int statusCode = rsp.getStatus();
                    String statusReason = rsp.getStatusInfo().getReasonPhrase();
                    if (debug) System.out.println("Status: " + statusCode + " - " + family.toString());
                    if (debug) System.out.println("Outcome: " + statusReason);
                    waitsecs = 5;
                }

            } catch (ProcessingException ex) {
                if (debug) System.out.println(ex.getMessage());
                waitsecs = 5;

            } finally {

                if (rsp != null) {
                    rsp.close();
                }

                if (waitsecs > 0) {

                    if (debug) System.out.println("Retry in " + waitsecs + " secs");

                    try {
                        Thread.sleep(waitsecs * 1000);
                    } catch (InterruptedException ex) { }
                }
            }
        }
    }

    public String substituteJsonFields(String src, String json) {

        Pattern p = Pattern.compile("\\{([^\\}]*)\\}");
        Matcher m = p.matcher(src);
        StringBuffer sb = new StringBuffer();

        Configuration cnf = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
        DocumentContext parse = JsonPath.using(cnf).parse(json);

        while (m.find()) {
            String sub = parse.read(m.group(1));
            if (sub == null) sub = "";
            m.appendReplacement(sb, sub);
        }

        m.appendTail(sb);
        return sb.toString();
    }
    
    public void log(String msg) {
        if (debug) {
            String timestamp = (new SimpleDateFormat("HH:mm:ss")).format(new Date());
            System.out.printf("%s [%s] %s\n", timestamp, getName(), msg);                        
        }
    }
    
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void addVariable(String name, String val) {
        varmap.put(name, val);
    }

    public void addTag(String tag) {
        taglist.add(tag);
    }

    public void useContextService(boolean usectx) {
        this.usectx = usectx;
    }

    public void addContextSearchField(String cusfld, String val) {
        ctxsrchmap.put(cusfld, val);
    }
 
    public void addContextPodField(String podfld, String val) {
        ctxpodmap.put(podfld, val);
    }
}
