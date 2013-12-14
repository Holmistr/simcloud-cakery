package org.perfcake.message.sender;

import java.io.Serializable;
import java.util.Map;
import java.util.Random;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.perfcake.message.Message;
import org.perfcake.reporting.MeasurementUnit;

/**
 * TODO: document this properly
 * <p/>
 * Sender for measuring communication with Infinispan HotRod server.
 */
public class IspnCakeryHotRodSender extends AbstractSender {

    private RemoteCache cache;
    private Random rand;

    /**
     * This method is called once for needed initializations.
     * @throws Exception
     */
    @Override
    public void init() throws Exception {
        System.out.println("Doing Init in " + this.getClass().getName());
        //API entry point, by default it connects to localhost:11222
        RemoteCacheManager remoteCacheManager = new RemoteCacheManager();
        this.cache = remoteCacheManager.getCache();
        for(int i=0; i<100; i++) {
            cache.put("car"+i, "carValue"+i);
        }
        this.rand = new Random();
    }

    @Override
    public void close() {
        // nop
    }

//    @Override
//    public void preSend(final Message message, final Map<String, String> properties) throws Exception {
//        super.preSend(message, properties);
//    }

    @Override
    public Serializable doSend(final Message message, final Map<String, String> properties, final MeasurementUnit mu) throws Exception {
        System.out.println("Doing Send in " + this.getClass().getName());

//        if (message != null)
//            System.out.println(message.toString());
//        if (properties != null)
//            System.out.println(properties.toString());
//        if (mu != null)
//            System.out.println(mu.toString());

        System.out.println("value: " + cache.get("car"+rand.nextInt(99)).toString());

        return null;
    }

    @Override
    public void postSend(final Message message) throws Exception {

    }

}
