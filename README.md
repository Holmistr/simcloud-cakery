Infinispan-cakery is a performance stress project for (not only) Infinispan servers using PerfCake framework for benchmarking.

How to start it?

For benchmarking Infinispan OData server:
(It requires running Infinispan OData server on localhost)

mvn clean package exec:java -Dscenario=OData-and-REST-scenario -Dthreads=1 -DrunType=time -Dduration=60000
 -DthreadQueueSize=30000 -DserviceUri=http://localhost:8887/ODataInfinispanEndpoint.svc/
 -DcacheName=mySpecialNamedCache -DnumberOfEntries=1000

For benchmarking Infinispan REST server:
(It requires running Infinispan REST server on localhost)

mvn clean package exec:java -Dscenario=OData-and-REST-scenario -Dthreads=1 -DrunType=time -Dduration=60000
 -DthreadQueueSize=30000 -DserviceUri=http://localhost:8080/rest/
 -DcacheName=default -DnumberOfEntries=1000

Just Christmas commit :P
