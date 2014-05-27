Infinispan-cakery is a performance stress application for (not only) Infinispan servers using PerfCake framework.

---------------------------------------------------------------------------------
**NOTE1:** It requires running Infinispan OData server on localhost,
make sure server is started with configured "mySpecialNamedCache" cache,
or change desired senders to use your own cache name.

**NOTE2:** In order to measure memory uncomment MemoryUsageReporter in particular scenario.
In that case, PerfCake agent need to run on measured system.
Add -javaagent:/path/to/repository/org/perfcake/perfcake/1.0/perfcake-1.0.jar=hostname=127.0.0.1,port=8850 when starting server:

**NOTE3:** It requires internet connection as PerfCake scenario schema needs to be checked.

**Start Infinispan OData server:**

*java -javaagent:/path/to/repository/org/perfcake/perfcake/1.0/perfcake-1.0.jar=hostname=127.0.0.1,port=8850
 -Xms512m -Xmx512m -Djava.net.preferIPv4Stack=true -jar /path/to/infinispan-odata-server-1.0-SNAPSHOT.jar
 http://localhost:8887/ODataInfinispanEndpoint.svc/ infinispan-dist.xml*

---------------------------------------------------------------------------------

Run Maven commands from the main infinispan-cakery folder:

**For benchmarking Infinispan OData server:**

mvn clean package exec:java -Dperfcake.agent.host=127.0.0.1 -Dscenario=OData-and-REST-scenario -Dthreads=1 -DrunType=time -Dduration=60000 -DthreadQueueSize=30000 -DserviceUri=http://localhost:8887/ODataInfinispanEndpoint.svc/ -DcacheName=mySpecialNamedCache -DnumberOfEntries=1000

**For benchmarking Infinispan REST server:**
(It requires running Infinispan REST server on localhost, Sender uses "default" cache name + see NOTE2 ^)

mvn clean package exec:java -Dperfcake.agent.host=127.0.0.1 -Dscenario=OData-and-REST-scenario -Dthreads=1 -DrunType=time -Dduration=60000 -DthreadQueueSize=30000 -DserviceUri=http://localhost:8080/rest/ -DcacheName=default -DnumberOfEntries=1000


