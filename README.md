Infinispan-cakery is a performance stress application for (not only) Infinispan servers using PerfCake framework.

How to start it?

For benchmarking Infinispan OData server:

---------------------------------------------------------------------------------
NOTE1: It requires running Infinispan OData server on localhost,
make sure server is started with configured "mySpecialNamedCache" cache,
or change desired senders to use your own cache name.

NOTE2: In order to measure memory uncomment MemoryUsageReporter in particular scenarion.
In that case, PerfCake agent need to run on measured system.
Add -javaagent:/path/to/repository/org/perfcake/perfcake/1.0/perfcake-1.0.jar=hostname=127.0.0.1,port=8850 when starting server:

Starting Infinispan OData server (or see its README.md)
java -javaagent:/path/to/repository/org/perfcake/perfcake/1.0/perfcake-1.0.jar=hostname=127.0.0.1,port=8850
 -Xms512m -Xmx512m -Djava.net.preferIPv4Stack=true -jar /path/to/infinispan-odata-server-1.0-SNAPSHOT.jar
 http://localhost:8887/ODataInfinispanEndpoint.svc/ infinispan-dist.xml
---------------------------------------------------------------------------------

mvn clean package exec:java -Dscenario=OData-and-REST-scenario -Dthreads=1 -DrunType=time -Dduration=60000
 -DthreadQueueSize=30000 -DserviceUri=http://localhost:8887/ODataInfinispanEndpoint.svc/
 -DcacheName=mySpecialNamedCache -DnumberOfEntries=1000

For benchmarking Infinispan REST server:
(It requires running Infinispan REST server on localhost, Sender uses "defautl" cache name + see NOTE2 ^)

mvn clean package exec:java -Dscenario=OData-and-REST-scenario -Dthreads=1 -DrunType=time -Dduration=60000
 -DthreadQueueSize=30000 -DserviceUri=http://localhost:8080/rest/
 -DcacheName=default -DnumberOfEntries=1000


