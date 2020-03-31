Java Standalone SDK
===================

Client for submission of signals to [Sqreen’s signal ingestion
endpoint](https://ingestion.sqreen.com/). Usage of the [Sqreen
Agent](https://docs.sqreen.com/java/introduction/) is not required, although
facilities are provided to read the service’s credentials used by the agent
(requires version >= 2.0 of the Agent).

This library currently provides little in the way of structure for the signals
themselves. Only the bare syntactic general requirements of the signals are
enforced.  Because the Signal type itself is so generic, the library does not
actually help writing signals that the ingestion backend understands.

The API actually accepts also arbitrary objects and lets the user configure its
serialization to the format that the ingestion endpoint expects.

Put another way, this is currently a fairly low-level library.


Bare Usage of the Client
------------------------

```java
// import io.sqreen.sasdk.backend.*;

IngestionHttpClient.WithAuthentication service = new IngestionHttpClientBuilder()
        .buildingHttpClient()
        .withConnectionTimeoutInMs(5000)
        .withReadTimeoutInMs(10000)
        .withProxy("http://proxy.com/")
        .buildHttpClient()
        .withErrorListener(IngestionErrorListener.LoggingIngestionErrorListener.INSTANCE)
        .createWithAuthentication(
                IngestionHttpClientBuilder.authConfigWithAPIKey("apiKey", "appName"));

service.reportSignal(mySignal);

service.close();

```

Requests are run synchronously.

Batch collector
---------------

Signals can also be batched. The batches are sent once a certain number of
objects have been collected or once certain time passes.

```java
BatchCollector coll = BatchCollector.builder(service)
        .withTriggerSize(10)
        .withMaxConcurrentRequests(3)
        .withMaxQueueSize(50)
        .withMaxDelayInMs(30000)
        .build();

coll.add(mySignal);

coll.close();
```


`BatchCollector` does not take ownership of `service`, which must still be
closed separately.

<!-- vim: set et tw=80 ai spell: -->
