package org.perfcake.message.sender;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.util.Map;
import java.util.Random;

import org.apache.http.HttpEntity;
import org.apache.http.NoHttpResponseException;
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
import org.apache.log4j.Logger;
import org.perfcake.message.Message;
import org.perfcake.reporting.MeasurementUnit;


/**
 * A mutual sender for both OData and REST.
 * <p/>
 * It makes sure that approach for measuring Infinispan OData and REST server
 * is consistent and there are used only different URIs, hosts, ports, etc. in this Sender.
 * <p/>
 * <p/>
 * An entry is generated JSON document with unique ID, approximately about 20 KB+ size.
 * 20 k entries occupy approx. 450 MB of heap size.
 * 40 k entries ~~> 900 MB x numOfOwner=2 (dist mode) ~~> approx. 1,8 GB of data.
 * <p/>
 * Server started with Xmx and Xms=4096m, no more than a half of heap size should be occupied (JDG QE).
 */
public class IspnCakeryODataAndRestSender extends AbstractSender {

    private Logger log = Logger.getLogger(IspnCakeryODataAndRestSender.class);

    private CloseableHttpClient httpClient = HttpClients.createDefault();
    private CloseableHttpClient httpClientForPost = HttpClients.createDefault();

    // Every thread in the scenario is about to run init() but we need to run it only once.
    private boolean initDone = false;
    // Retry to repeat failed put just once.
    private boolean failedPutRetried = false;

    // parameters passed as System properties using -Dproperty=value
    // TODO: add defaults
    private String serviceUri;
    private String cacheName;
    private String perfcakeAgentHost;
    private int numOfEntries = 1;
    private int requestSleepTimeMillis = 0;

    private Random rand = new Random();
    private volatile int numOfPutErrors = 0;
    private volatile int numOfGetErrors = 0;

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
//      OData: http://{bind_node}:8887/ODataInfinispanEndpoint.svc/
//      Rest: http://127.0.0.1:8080/rest/
//      String cacheName = "mySpecialNamedCache" "defaultCache"
        serviceUri = System.getProperty("serviceUri");
        cacheName = System.getProperty("cacheName");
        perfcakeAgentHost = System.getProperty("perfcake.agent.host").replace(".", "");

        if (System.getProperty("requestSleepTimeMillis") != null) {
            requestSleepTimeMillis = Integer.parseInt(System.getProperty("requestSleepTimeMillis"));
        }
        log.info("requestSleepTimeMillis set to: " + requestSleepTimeMillis);

        numOfEntries = Integer.parseInt(System.getProperty("numberOfEntries"));
        initDone = Boolean.parseBoolean(System.getProperty("initDone"));

        // each thread in PerfCake is about to process its own init() method once.
        if (!initDone) {

            log.info("Setting system property initDone to value: true... other threads should see it.");
            // this Thread is responsible for filling cache
            System.setProperty("initDone", "true");
            initDone = true;

            long start = System.currentTimeMillis();

            log.info("Starting init() method in: " + this.getClass().getName());

            String entryKey;
            String jsonPerson = null;
            String post;

            for (int i = 1; i <= numOfEntries; i++) {

                entryKey = "person" + i + "-" + perfcakeAgentHost;
                jsonPerson = createJsonPersonString(
                        "org.infinispan.odata.Person", "person" + i, "MALE", "John", "Smith", 24);

                if (i % 100 == 0) {
                    log.info("\n" + i + " entryKey = " + entryKey + "\n");
                }

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

                    // reset failure notificator for next put
                    failedPutRetried = false;

                } catch (NoHttpResponseException e) {
                    // server failed to respond -- retry, it is needed to store that object
                    if (!failedPutRetried) {
                        i--;
                        log.error("Server failed to respond, RETRY, decreasing counter by 1 to " + i + " \n message: " + e.getMessage());
                        numOfPutErrors++;
                        failedPutRetried = true;
                    } else {
                        log.error("This put was already retried and failed again - give up, i = " + i + " " + e.getMessage());
                    }

                } catch (SocketException e) {
                    // server failed to respond -- retry, it is needed to store that object
                    if (!failedPutRetried) {
                        i--;
                        log.error("Server failed to respond, RETRY, decreasing counter by 1 to " + i + " \n message: " + e.getMessage());
                        numOfPutErrors++;
                        failedPutRetried = true;
                    } else {
                        log.error("This put was already retried and failed again - give up, i = " + i + " " + e.getMessage());
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            log.info("\n\n Init method took: " + (System.currentTimeMillis() - start) + " milliseconds");
            log.info("\n\n Number of errors during Init method (numberOfPutErrors): " + numOfPutErrors + " \n");
        } else {
            log.info("Init() method was already initialized by the first thread; skipping init().");
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
            get = serviceUri + "" + cacheName + "_get?key=%27" + "person" + (rand.nextInt(numOfEntries) + 1) +
                    "-" + perfcakeAgentHost + "%27";

            // OData interface approach (NOT SUPPORTED YET)
            // + this is slow, problems with a closing of streams
            // (we use OData service operations as a workaround; to gain control over output stream)
            // get = serviceUri + "" + cacheName + "%28%27" + "person" + (rand.nextInt(numOfEntries)+1) + "%27%29";

        } else {
            // REST
            get = serviceUri + "" + cacheName + "/person" + (rand.nextInt(numOfEntries) + 1) + "-" + perfcakeAgentHost;
        }

        HttpGet httpGet = new HttpGet(get);
        httpGet.setHeader("Accept", "application/json; charset=UTF-8");
        try {

            // reuse one client + set: echo "1" >/proc/sys/net/ipv4/tcp_tw_reuse (on local machine)
            CloseableHttpResponse response = httpClient.execute(httpGet);

            if (response.getStatusLine().getStatusCode() == 404) {
                // entry not found
                numOfGetErrors++;
                log.error("Status CODE = 404 for " + get + " \n numberOfGetErrors: " + numOfGetErrors);
            }

            HttpEntity entity = response.getEntity();

            if (entity == null) {
                numOfGetErrors++;
                log.error("Entity is null :( Bad returned? Nonexistent entry? " +
                        get + " " + response.getStatusLine().getStatusCode() + " numberOfGetErrors: " + numOfGetErrors);
                return null;
            }

            EntityUtils.consume(entity);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            numOfGetErrors++;
            log.error("IOException during gets (probably overloaded), numberOfGetErrors: " + numOfGetErrors + " \n ");
            e.printStackTrace();
        } catch (Exception e) {
            numOfGetErrors++;
            log.error("Exception during gets (probably overloaded), numberOfGetErrors: " + numOfGetErrors + " \n ");
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void postSend(final Message message) {
        try {
            if (requestSleepTimeMillis > 0) {
                Thread.sleep(requestSleepTimeMillis);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Return OData standardized JSON (represented as String)
     * This can be passed as content (StringEntity) of HTTP POST request
     * <p/>
     * // TODO -- generate large entries of size passed by -Dproperty
     * // TODO -- move it to UTILs class (Entry generation  is the same for all Senders)
     * // TODO -- so not create StringBuilder object for every put
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

        sb.append("{");
        sb.append("\"entityClass\":\"" + entityClass + "\",\n");
        sb.append("\"id\":\"" + id + "\",\n");
        sb.append("\"gender\":\"" + gender + "\",\n");
        sb.append("\"firstName\":\"" + firstName + "\",\n");
        sb.append("\"lastName\":\"" + lastName + "\",\n");

        // Source: http://www.javamex.com/tutorials/memory/string_memory_usage.shtml
        // 1 java char = 2 bytes
        // 100 000 chars = 200 000 bytes = approx. 200 KB
        // 10 000 entries x 200 kB = approx. 2 GB of data
        // or 20 000 entries with 50 000 chars = approx. 2 GB of data

        // or 100 000 entries with 10 000 chars (=20 kB) (1 large document) = approx. 2 GB of data

        // This is approximately 20,3 kB entry
//        char[] chars = new char[10000];
//        Arrays.fill(chars, 'x');
//        String payload = new String(chars);
//
//        sb.append("\"documentString\":\"" + payload + "\",\n");

        sb.append("\"age\":" + age + "\n");
        sb.append("}");

        return sb.toString();
    }
}
