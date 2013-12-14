package org.perfcake.message.sender;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Random;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.perfcake.message.Message;
import org.perfcake.reporting.MeasurementUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract sender for both OData and REST Sender.
 * <p/>
 * It makes sure that approach for measuring Infinispan OData and REST server
 * is consistent and we are using only different URIs, hosts, ports, etc.
 */
public class IspnCakeryODataAndRestSender extends AbstractSender {

    private Logger log = LoggerFactory.getLogger(IspnCakeryODataAndRestSender.class);

    // we will reuse this client object
    private CloseableHttpClient httpClient = HttpClients.createDefault();
    private CloseableHttpClient httpClientForPost = HttpClients.createDefault();

    // Every thread in the scenario is about to run init() but we need to run it only once.
    private boolean initDone = false;
    // parameters passed as System properties using -Dproperty=value
    // TODO: add defaults
    private String serviceUri;
    private String cacheName;
    private int numOfEntries = 1;

    private int requestSleepTimeMillis = 0;

    private Random rand = new Random();


    /**
     * This method is called once by each thread for needed initializations.
     * It depends on test logic, but if we need firstly put data into the cache
     * and then process only gets, do filling part of init() only once
     * <p/>
     * initDone system property is used for driving this logic
     * when the first thread set it up, other threads can see it
     *
     * @throws Exception
     */
    @Override
    public void init() throws Exception {

//      OData: http://localhost:8887/ODataInfinispanEndpoint.svc/
//      Rest: http://127.0.0.1:8080/rest/
//      String cacheName = "mySpecialNamedCache" "default"
        serviceUri = System.getProperty("serviceUri");
        cacheName = System.getProperty("cacheName");
        requestSleepTimeMillis = Integer.parseInt(System.getProperty("requestSleepTimeMillis"));

        System.out.println("requestSleepTimeMillis set to: " + requestSleepTimeMillis);

        numOfEntries = Integer.parseInt(System.getProperty("numberOfEntries"));
        initDone = Boolean.parseBoolean(System.getProperty("initDone"));

        // need to decide according to some parent object because every thread has it's own init
        if (!initDone) {

            System.out.println("Setting system property initDone to value: true... other threads should see it.");
            // immediately let other threads know that this is Thread responsible for filling cache
            System.setProperty("initDone", "true");
            initDone = true;

            long start = System.currentTimeMillis();

            System.out.println("Doing Init in " + this.getClass().getName());

            for (int i = 1; i <= numOfEntries; i++) {

                Thread.sleep(50);

                if (i % 100 == 0) {
                    System.out.println("\n\n\n " + i + "\n\n\n");
                }

                String entryKey = "person" + i;
                String jsonPerson = createJsonPersonString(
                        "org.infinispan.odata.Person", "person" + i, "MALE", "John", "Smith", 24);

                String post;
                if (serviceUri.contains(".svc")) {
                    // OData
                    post = serviceUri + "" + cacheName + "_put?IGNORE_RETURN_VALUES=%27true%27&key=%27" + entryKey + "%27";
                } else {
                    // REST
                    post = serviceUri + "" + cacheName + "/" + entryKey;
                }

                HttpPost httpPost = new HttpPost(post);
                httpPost.setHeader("Content-Type", "application/json; charset=UTF-8");
                httpPost.setHeader("Accept", "application/json; charset=UTF-8");

                try {
                    StringEntity se = new StringEntity(jsonPerson, HTTP.UTF_8);
                    se.setContentEncoding(new BasicHeader(HTTP.CONTENT_ENCODING, "charset=UTF-8"));
                    se.setContentType("application/json; charset=UTF-8");
                    httpPost.setEntity(se);

                    CloseableHttpResponse response = httpClientForPost.execute(httpPost);
                    System.out.println("Response code of http post: " + response.getStatusLine().getStatusCode());
                    EntityUtils.consume(response.getEntity());

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("\n\n Init method took: " + (System.currentTimeMillis() - start));
        } else {
            System.out.println("Init() method was already initialized by the first thread, skipping to doSend().");
            log.info("Init() method was already initialized by the first thread, skipping to doSend().");
        }
    }

    @Override
    public void close() {
        // nop
    }

    @Override
    public void preSend(final Message message, final Map<String, String> properties) throws Exception {
        super.preSend(message, properties);
    }

    @Override
    public Serializable doSend(final Message message, final Map<String, String> properties, final MeasurementUnit mu) throws Exception {

        String get;
        if (serviceUri.contains(".svc")) {
            // OData service operation approach
            get = serviceUri + "" + cacheName + "_get?key=%27" + "person" + (rand.nextInt(numOfEntries)+1) + "%27";

            // OData interface approach (NOT SUPPORTED YET)
            // + this is slow, OData is not closing streams
            // (we use functional approach for workaround this, to gain control over output stream)
            // get = serviceUri + "" + cacheName + "%28%27" + "person" + (rand.nextInt(numOfEntries)+1) + "%27%29";
        } else {
            // REST
            get = serviceUri + "" + cacheName + "/person" + rand.nextInt(numOfEntries);
        }

        HttpGet httpGet = new HttpGet(get);
        httpGet.setHeader("Accept", "application/json; charset=UTF-8");
        try {

            // reuse one client + set: echo "1" >/proc/sys/net/ipv4/tcp_tw_reuse
            CloseableHttpResponse response = httpClient.execute(httpGet);

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                System.out.println("Entity is null :( Bad returned? Nonexistent entry? " +
                        get + " " + response.getStatusLine().getStatusCode());
                return null;
            }

            EntityUtils.consume(entity);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void postSend(final Message message) {
        try {
            Thread.sleep(requestSleepTimeMillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Return OData standardized JSON (represented as String)
     * This can be passed as content (StringEntity) of HTTP POST request
     *
     * @param entityClass
     * @param id
     * @param gender
     * @param firstName
     * @param lastName
     * @param age
     * @return Standardized OData JSON person entity as String.
     */
    public static String createJsonPersonString(String entityClass, String id,
                                                String gender, String firstName, String lastName, int age) {

        StringBuilder sb = new StringBuilder();

        // TODO: make it bigger + measure size of entry + pass entrySize=Xkb? and according to this
        // TODO: system property passed for test scenario, generate such a big entries

        // according do OData JSON format standard
//        sb.append("{\"d\" : {\"jsonValue\" : ");
        sb.append("{");
        sb.append("\"entityClass\":\"" + entityClass + "\",\n");
        sb.append("\"id\":\"" + id + "\",\n");
        sb.append("\"gender\":\"" + gender + "\",\n");
        sb.append("\"firstName\":\"" + firstName + "\",\n");
        sb.append("\"lastName\":\"" + lastName + "\",\n");
        sb.append("\"documentString\":\"" + "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ " +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ " +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ " +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ" + "\",\n");
        sb.append("\"age\":" + age + "\n");
        sb.append("}");
//        sb.append("}}");

        return sb.toString();
    }

}
