package com.cisco.pt.cceuq;

import com.cisco.thunderhead.client.ClientResponse;
import com.cisco.thunderhead.client.ContextServiceClient;
import static com.cisco.thunderhead.client.Operation.OR;
import com.cisco.thunderhead.client.SearchParameters;
import com.cisco.thunderhead.customer.Customer;
import com.cisco.thunderhead.datatypes.PodMediaType;
import com.cisco.thunderhead.pod.Pod;
import com.cisco.thunderhead.util.DataElementUtils;
import com.cisco.thunderhead.util.SDKUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;


public class CustomerContext {

    ContextServiceClient ctxsvc = TaskApiClient.ctxcon;
    String cusid;

    public String getCustomer(SearchParameters lookupParams) {

// Locate customer using search fields supplied                           
                            
        Map<String, Object> initCustData = new TreeMap<>();

// Initialise data for creating new customer using search params

        lookupParams.forEach((k, l) -> {
            l.forEach(v -> {
                initCustData.put(k, v);
            });
        });

        List<Customer> custList = ctxsvc.search(Customer.class, lookupParams, OR);
        System.out.println("\n *** Entries returned = " + custList.size() + "\n");

        Customer cus;

        if (custList.isEmpty()) {

// Customer not found so create new one using supplied data for customer identification

            cus = new Customer(DataElementUtils.convertDataMapToSet(initCustData));
            cus.setFieldsets(new ArrayList<>(Arrays.asList("cisco.base.customer")));

            ClientResponse ctxrsp = ctxsvc.create(cus);
            cusid = SDKUtils.getIdFromResponse(ctxrsp);
            System.out.println("\n *** New customer added: " + cusid + "\n");

        } else {
            cus = custList.get(0);
            cusid = cus.getCustomerId().toString();
            System.out.println("\n *** Located customer: " + cusid + "\n");
        }
                
// Return customer data in JSON format

        StringBuilder json = new StringBuilder();

        json.append("{\"CustomerID\":\"").append(cusid).append("\",");

        cus.getDataElements().forEach(d -> {                    
            json.append("\"").append(d.getDataKey()).append("\":\"").append(d.getDataValue().toString()).append("\",");
        });

        json.replace(json.length() - 1, json.length(), "}");

        return json.toString();
    }
    
    protected String addPod(Map podContextData) {

// Add PoD to customer using supplied task event information

        Pod pod = new Pod(DataElementUtils.convertDataMapToSet(podContextData));
        pod.setCustomerId(UUID.fromString(cusid));
        pod.setMediaType(PodMediaType.EVENT);
        pod.setFieldsets(new ArrayList<>(Arrays.asList("cisco.base.pod")));
        ClientResponse ctxrsp = ctxsvc.create(pod);

        System.out.println("\n *** ID of new pod: " + SDKUtils.getIdFromResponse(ctxrsp) + "\n");
        
        return ctxrsp.getLocation().toString();
    }
}
