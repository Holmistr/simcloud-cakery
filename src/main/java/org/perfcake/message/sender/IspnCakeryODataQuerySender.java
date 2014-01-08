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
import org.utils.IspnCakeryUtils;


/**
 * Infinispan OData server query sender using OData query language.
 *
 * @author Tomas Sykora <tomas@infinispan.org>
 */
public class IspnCakeryODataQuerySender extends AbstractSender {

    private Logger log = Logger.getLogger(IspnCakeryODataQuerySender.class);

    private CloseableHttpClient httpClient = HttpClients.createDefault();
    private CloseableHttpClient httpClientForPost = HttpClients.createDefault();

    // Every thread in the scenario is about to run init() but we need to run it only once.
    private boolean initDone = false;
    // Retry to repeat failed put just once.
    private boolean failedPutRetried = false;

    // parameters passed as System properties using -Dproperty=value
    private String serviceUri;
    private String cacheName;
    private String perfcakeAgentHost = "";
    private int numOfEntries = 1;
    private int requestSleepTimeMillis = 0;

    private Random rand = new Random();
    private volatile int numOfPutErrors = 0;
    private volatile int numOfGetErrors = 0;

    private String get;

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
            String jsonPerson;
            String post;

            for (int i = 1; i <= numOfEntries; i++) {

                entryKey = "person" + i + "appendix" + perfcakeAgentHost;
                jsonPerson = IspnCakeryUtils.createJsonPersonString(
                        "org.infinispan.odata.Person", entryKey, "MALE", "John", "Smith", 24);


                if (i % 100 == 0) {
                    log.info("\n" + i + " entryKey = " + entryKey + "\n");
                }


                // OData
                post = serviceUri + "" + cacheName + "_put?IGNORE_RETURN_VALUES=%27true%27&key=%27" + entryKey + "%27";

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

        // OData service operation + QUERY approach
        get = serviceUri + "" + cacheName +
                "_get?$filter=id%20eq%20%27" + "person" + (rand.nextInt(numOfEntries) + 1) +
                "appendix" + perfcakeAgentHost + "%27";

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
}
