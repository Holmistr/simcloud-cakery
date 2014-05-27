package org.perfcake.message.sender;

import java.io.Serializable;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.infinispan.cakery.MemcachedClient;
import org.perfcake.message.Message;
import org.perfcake.reporting.MeasurementUnit;
import org.utils.IspnCakeryUtils;

/**
 * Sender for Memcached.
 *
 * @author Tomas Sykora <tomas@infinispan.org>
 */
public class IspnCakeryMemcachedSender extends AbstractSender {

    private Logger log = Logger.getLogger(IspnCakeryMemcachedSender.class);
    // Every thread in the scenario is about to run init() but we need to run it only once.
    private boolean initDone = false;
    private int numOfEntries = 1;
    private int r = 0; // random

    private int requestSleepTimeMillis = 0;
    private Random rand = new Random();

    private String perfcakeAgentHost="";

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

        if (System.getProperty("requestSleepTimeMillis") != null) {
            requestSleepTimeMillis = Integer.parseInt(System.getProperty("requestSleepTimeMillis"));
            log.info("requestSleepTimeMillis set to: " + requestSleepTimeMillis);
        }

        numOfEntries = Integer.parseInt(System.getProperty("numberOfEntries"));
        initDone = Boolean.parseBoolean(System.getProperty("initDone"));

        if (System.getProperty("perfcake.agent.host") != null) {
            perfcakeAgentHost = System.getProperty("perfcake.agent.host").replace(".", "");
        }

        mc1 = new MemcachedClient(ENCODING, System.getProperty("memcached.host"), 11211, 10000); // to run against

        // decide according to system property because every thread has it's own init
        if (!initDone) {

            log.info("Setting system property initDone to value: true... other threads should see it.");
            // immediately let other threads know that this is Thread responsible for filling cache
            System.setProperty("initDone", "true");
            initDone = true;

            long start = System.currentTimeMillis();
            log.info("Doing Init in " + this.getClass().getName());

            String entryKey;
            String jsonPerson;

            for (int i = 1; i <= numOfEntries; i++) {

                entryKey = "person" + i + "appendix" + perfcakeAgentHost;
                jsonPerson = IspnCakeryUtils.createJsonPersonString(
                        "org.infinispan.odata.Person", "person" + i, "MALE", "John", "Smith", 24);

                if (i % 100 == 0) {
                    log.info("\n" + i + " entryKey = " + entryKey + "\n");
                }

                mc1.set(entryKey, jsonPerson);
            }

            log.info("\n Init method took: " + (System.currentTimeMillis() - start));
        } else {
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

        r = rand.nextInt(numOfEntries)+1;

        if (mc1.get("person" + r + "appendix" + perfcakeAgentHost) == null) {
            log.error("Memcached: Entity is null :( Bad returned? Nonexistent entry? Entry key: " +
                    ("person" + r + "appendix" + perfcakeAgentHost));
        // throw new Exception("Memcached: value for key person" + r + "appendix" + perfcakeAgentHost + " is NULL");
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
