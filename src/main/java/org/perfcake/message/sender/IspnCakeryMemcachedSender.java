package org.perfcake.message.sender;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.infinispan.cakery.MemcachedClient;
import org.perfcake.message.Message;
import org.perfcake.reporting.MeasurementUnit;

/**
 * An abstract sender for Hot Rod.
 * <p/>
 */
public class IspnCakeryMemcachedSender extends AbstractSender {

    private Logger log = Logger.getLogger(IspnCakeryMemcachedSender.class);
    // Every thread in the scenario is about to run init() but we need to run it only once.
    private boolean initDone = false;
    private int numOfEntries = 1;
    private int r = 0; // random

    private int requestSleepTimeMillis = 0;
    private Random rand = new Random();

    static final String ENCODING = "UTF-8";
    private MemcachedClient mc1;


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

        requestSleepTimeMillis = Integer.parseInt(System.getProperty("requestSleepTimeMillis"));

        log.info("requestSleepTimeMillis set to: " + requestSleepTimeMillis);

        numOfEntries = Integer.parseInt(System.getProperty("numberOfEntries"));
        initDone = Boolean.parseBoolean(System.getProperty("initDone"));

        mc1 = new MemcachedClient(ENCODING, System.getProperty("memcached.host"), 11211, 10000); // to run against

        // need to decide according to some parent object because every thread has it's own init
        if (!initDone) {

            log.info("Setting system property initDone to value: true... other threads should see it.");
            // immediately let other threads know that this is Thread responsible for filling cache
            System.setProperty("initDone", "true");
            initDone = true;
            requestSleepTimeMillis = Integer.parseInt(System.getProperty("requestSleepTimeMillis"));

            long start = System.currentTimeMillis();
            log.info("Doing Init in " + this.getClass().getName());

            for (int i = 1; i <= numOfEntries; i++) {

                String entryKey = "person" + i;
                String jsonPerson = createJsonPersonString(
                        "org.infinispan.odata.Person", "person" + i, "MALE", "John", "Smith", 24);

                mc1.set(entryKey, jsonPerson);
            }

            log.info("\n Init method took: " + (System.currentTimeMillis() - start));
        } else {
            log.info("Init() method was already initialized by the first thread, skipping to doSend().");
        }
    }

    @Override
    public void close() {
        try {
            mc1.close();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public void preSend(final Message message, final Map<String, String> properties) throws Exception {
        super.preSend(message, properties);
    }

    @Override
    public Serializable doSend(final Message message, final Map<String, String> properties, final MeasurementUnit mu) throws Exception {

        r = rand.nextInt(numOfEntries)+1;

        if (mc1.get("person" + r) == null) {
            throw new Exception("HotRod: value for key person" + r + " is NULL");
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
        // TODO: system property passed for test scenario, generate such big entries

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