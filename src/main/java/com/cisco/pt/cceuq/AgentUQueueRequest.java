package com.cisco.pt.cceuq;

import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.*;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

public class AgentUQueueRequest implements AutoCloseable {

    int statusCode;
    String statusReason;
    String errorReason;
    String taskStatus;
    String taskStatusReason;
    String taskEstWaitTime;
    String taskId;
    String refUrl;

    private Client client;
    private boolean acceptCerts = true;
    private String protocol = "HTTP";
    private String socminerPort;
    private final String socminerHost;
    private final String socminerFeed;
    private final Map<String, String> reqfields;
    private final Map<String, String> eccs;
    private final Map<String, String> miscvars;
    private final Map<CallVariable, String> cvs;
    private final List<String> tags;

    public enum CallVariable {cv_1, cv_2, cv_3, cv_4, cv_5, cv_6, cv_7, cv_8, cv_9, cv_10}
    

    public AgentUQueueRequest(String host, String feedId, String scriptSelector) {
        this.socminerHost = host;
        this.socminerFeed = feedId;

        cvs = new HashMap();
        eccs = new HashMap();
        miscvars = new HashMap();
        tags = new ArrayList();

        reqfields = new HashMap();
        reqfields.put("title", this.getClass().getName());
        reqfields.put("name", "Anonymous");
        reqfields.put("scriptSelector", scriptSelector);
    }

    public void setSocminerPort(String socminerPort) {
        this.socminerPort = socminerPort;
    }

    public void setAcceptCerts(boolean acceptCerts) {
        this.acceptCerts = acceptCerts;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void setReqTitle(String reqTitle) {
        reqfields.put("title", reqTitle);
    }

    public void setReqName(String reqName) {
        reqfields.put("name", reqName);
    }

    public void setReqDescrip(String reqDescrip) {
        reqfields.put("description", reqDescrip);
    }

    public void setScriptSelector(String icmScriptSelector) {
        reqfields.put("scriptSelector", icmScriptSelector);
    }

    public void setCv1(String val) {
        cvs.put(CallVariable.cv_1, val);
    }

    public void setCv2(String val) {
        cvs.put(CallVariable.cv_2, val);
    }

    public void setCv3(String val) {
        cvs.put(CallVariable.cv_3, val);
    }

    public void setCv4(String val) {
        cvs.put(CallVariable.cv_4, val);
    }

    public void setCv5(String val) {
        cvs.put(CallVariable.cv_5, val);
    }

    public void setCv6(String val) {
        cvs.put(CallVariable.cv_6, val);
    }

    public void setCv7(String val) {
        cvs.put(CallVariable.cv_7, val);
    }

    public void setCv8(String val) {
        cvs.put(CallVariable.cv_8, val);
    }

    public void setCv9(String val) {
        cvs.put(CallVariable.cv_9, val);
    }

    public void setCv10(String val) {
        cvs.put(CallVariable.cv_10, val);
    }

    public void setCv(String cv, String val) {
        cvs.put(CallVariable.valueOf(cv), val);
    }

    public void setTag(String tag) {
        tags.add(tag);
    }

    public void setEcc(String eccName, String eccValue) {
        eccs.put(eccName, eccValue);
    }

    public void setVar(String varName, String varValue) {
        miscvars.put(varName, varValue);
    }


    public String status() {
        Response rsp = null;
        statusCode = 0;

        if (refUrl != null) {
            try {        
                rsp = client.target(refUrl)
                            .request()
                            .get();

                statusCode = rsp.getStatus();
                statusReason = rsp.getStatusInfo().getReasonPhrase();
                Response.Status.Family family = rsp.getStatusInfo().getFamily();
                System.out.println("Status: " + statusCode + " - " + family.toString());
                System.out.println("Outcome: " + statusReason);

                if (Response.Status.Family.SUCCESSFUL == family) {
                    statusReason = getAgentRequestStatus(rsp.readEntity(String.class));
                }

            } catch (ProcessingException ex) {
                statusReason = ex.getMessage();

            } finally {
                if (rsp != null) rsp.close();
            }

        } else {
            statusReason = "Task not created";
        }

        return statusReason;
    }


    public boolean delete() {
        Response rsp = null;
        statusCode = 0;
        boolean deleted = false;

        if (refUrl != null) {
            try {
                rsp = client.target(refUrl)
                            .request()
                            .delete();

                statusCode = rsp.getStatus();
                statusReason = rsp.getStatusInfo().getReasonPhrase();
                Response.Status.Family family = rsp.getStatusInfo().getFamily();
                deleted = Response.Status.Family.SUCCESSFUL == family;

                System.out.println("Status: " + statusCode + " - " + family.toString());
                System.out.println("Outcome: " + statusReason);

            } catch (ProcessingException ex) {
                statusReason = ex.getMessage();

            } finally {
                if (rsp != null) {
                    rsp.close();
                }
            }

        } else {
            statusReason = "Task not created";
        }

        return deleted;
    }


    public String create() {

        // Build task request data to be sent as HTTP XML body

        Element task = new Element("Task");
        
        reqfields.forEach((fld, val) -> {
            task.addContent(new Element(fld).setText(val));
        });

        Element variables;
        task.addContent(variables = new Element("variables"));

        cvs.forEach((cv, val) -> {
            val = val.trim();
            if (val.length() > 0) {
                if (val.length() > 40) val = val.substring(0, 40);
                variables.addContent(new Element("variable").addContent(new Element("name").setText(cv.name()))
                                                            .addContent(new Element("value").setText(val)));
            }
        });

        eccs.forEach((ecc, val) -> {
            val = val.trim();
            variables.addContent(new Element("variable").addContent(new Element("name").setText("user_" + ecc))
                                                        .addContent(new Element("value").setText(val)));
        });

        miscvars.forEach((var, val) -> {
            val = val.trim();
            variables.addContent(new Element("variable").addContent(new Element("name").setText(var))
                                                        .addContent(new Element("value").setText(val)));
        });

        Element tagblk;
        task.addContent(tagblk = new Element("tags"));
        
        tags.forEach(t -> {
            tagblk.addContent(new Element("tag").setText(t));
        });

        String body = new XMLOutputter(Format.getPrettyFormat()).outputString(task);
        System.out.println(body);

        // Build and invoke the request using XML body built above

        client = ClientBuilder.newBuilder()
                              .sslContext(getSSLContext(acceptCerts))
                              .hostnameVerifier(getHostnameVerifier())
                              .build()
                              .property(ClientProperties.CONNECT_TIMEOUT, 2000)
                              .property(ClientProperties.READ_TIMEOUT, 5000);

        String url = protocol + "://" + socminerHost;
        if (socminerPort != null) {
            url += ":" + socminerPort;
        }

        Response rsp = null;
        statusCode = 0;
        refUrl = null;
        
        try {
            rsp = client.target(url)
                        .path("ccp/task/feed/" + socminerFeed)
                        .request()
                        .post(Entity.entity(body, MediaType.APPLICATION_XML));

            statusCode = rsp.getStatus();
            statusReason = rsp.getStatusInfo().getReasonPhrase();
            Response.Status.Family family = rsp.getStatusInfo().getFamily();
            System.out.println("Status: " + statusCode + " - " + family.toString());
            System.out.println("Outcome: " + statusReason);

            if (Response.Status.Family.SUCCESSFUL != family) {
                errorReason = getErrorReason(rsp.readEntity(String.class));
                System.out.println("Reason: " + errorReason);
            } else {
                refUrl = rsp.getLocation().toString();
                System.out.println("Location: " + refUrl);
            }

        } catch (ProcessingException ex) {
            statusReason = ex.getMessage();

        } finally {
            if (rsp != null) rsp.close();
        }

        return refUrl;
    }


// Extract additional error information from the error response body if present

    private String getErrorReason(String rspxml) {
        String reason = null;

        try {
            SAXBuilder sb = new SAXBuilder();
            Document rspdoc = sb.build(new StringReader(rspxml));
            Element root = rspdoc.getRootElement();
            if ("apiErrors".equals(root.getName())) {
                String error_item = root.getChild("apiError").getChildText("errorData");
                String error_msg = root.getChild("apiError").getChildText("errorMessage");
                reason = error_item + ": " + error_msg;
            }
        } catch (JDOMException | IOException ex) {
        }

        return reason;
    }


// Extract request status information from response body if present

    private String getAgentRequestStatus(String rspxml) {

        try {
            SAXBuilder sb = new SAXBuilder();
            Document rspdoc = sb.build(new StringReader(rspxml));
            Element root = rspdoc.getRootElement();
            if ("Task".equals(root.getName()))
            {
                    taskStatus = root.getChildText("status");
                    taskStatusReason = root.getChildText("statusReason");
                    taskEstWaitTime = root.getChildText("estimatedWaitTime");
                    taskId = root.getChildText("taskID");
            }
        } catch (JDOMException | IOException ex) {
        }

        return taskStatus;
    }


// HTTPS / SSL context handling. An option is provided to accept all certificates without
// checking the truststore.
    
    private HostnameVerifier getHostnameVerifier() {
        return new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
                return true;
            }
        };
    }


    private SSLContext getSSLContext(boolean accept_certificates) {
        TrustManager tm_pass_all = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };

        SSLContext ctx = null;

        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509", "SunJSSE");
            tmf.init((java.security.KeyStore) null);
            TrustManager tms[] = accept_certificates ? new TrustManager[]{tm_pass_all} : tmf.getTrustManagers();

            ctx = SSLContext.getInstance("SSL");
            ctx.init(null, tms, null);
        } catch (GeneralSecurityException ex) {
        }

        return ctx;
    }


    @Override
    public void close() {

        if (client != null) client.close();
    }
}
