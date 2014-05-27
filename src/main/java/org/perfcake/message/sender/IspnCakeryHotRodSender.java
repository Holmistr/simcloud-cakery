package org.perfcake.message.sender;

import java.io.Serializable;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.perfcake.message.Message;
import org.perfcake.reporting.MeasurementUnit;
import org.utils.IspnCakeryUtils;

/**
 * Sender for Hot Rod.
 *
 * @author Tomas Sykora <tomas@infinispan.org>
 */
public class IspnCakeryHotRodSender extends AbstractSender {

    private Logger log = Logger.getLogger(IspnCakeryHotRodSender.class);

    // Every thread in the scenario is about to run init() but we need to run it only once.
    private boolean initDone = false;

    private RemoteCache cache = null;
    private int numOfEntries = 1;
    private int r = 0; // random
    private String perfcakeAgentHost = "perfCakeAgentHostNotSet";
    private String hotrodHost = "localhost";
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

        if (System.getProperty("requestSleepTimeMillis") != null) {
            requestSleepTimeMillis = Integer.parseInt(System.getProperty("requestSleepTimeMillis"));
            log.info("requestSleepTimeMillis set to: " + requestSleepTimeMillis);
        }

        numOfEntries = Integer.parseInt(System.getProperty("numberOfEntries"));
        initDone = Boolean.parseBoolean(System.getProperty("initDone"));

        if (System.getProperty("perfcake.agent.host") != null) {
            perfcakeAgentHost = System.getProperty("perfcake.agent.host").replace(".", "");
        }

        if (System.getProperty("hotrod.host") != null) {
            hotrodHost = System.getProperty("hotrod.host");
        }

        try {
            ConfigurationBuilder config = new ConfigurationBuilder();
            config.addServer().host(hotrodHost)
                    .port(11222);
            RemoteCacheManager remoteCacheManager = new RemoteCacheManager(config.build());
            this.cache = remoteCacheManager.getCache();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(e);
        }

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

                entryKey = "person" + i + "-" + perfcakeAgentHost;
                jsonPerson = IspnCakeryUtils.createJsonPersonString(
                        "org.infinispan.odata.Person", "person" + i, "MALE", "John", "Smith", 24);

                if (i % 100 == 0) {
                    log.info("\n" + i + " entryKey = " + entryKey + "\n");
                }

                cache.put(entryKey, jsonPerson);
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

        r = rand.nextInt(numOfEntries) + 1;

        if (cache.get("person" + r + "-" + perfcakeAgentHost) == null) {
            log.error("HotRod: Entity is null :( Bad returned? Nonexistent entry? Entry key: " + ("person" + r + "-" + perfcakeAgentHost));
            throw new Exception("HotRod: value for key person" + r + "-" + perfcakeAgentHost + " is NULL");
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
