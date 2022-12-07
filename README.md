# Overview

This connector enables the use of Hazelcast Pipelines to service HTTP Requests.  This approach separates the 
HTTP Connection handling and the response computation.  A regular Spring Boot application handles the HTTP connection 
and the Hazelcast cluster, running a Pipeline, handles the response computation.  

Some advantages of this approach are:
- The Spring Boot application is very light and stateless.  It is easy to scale simply by running multiple instances behind a load balancer.
- The Spring Boot application servers contain no business logic or data, making them safer to run in an internet facing subnet.
- The business logic is in an independently deployable Hazelcast pipeline.  The logic can be updated by deploying a new Pipeline, without touching the web servers.
- The web tier and the business logic tiers scale independently.

# Running the Example
A sample spring boot application that uses the Hazelcast pipeline dispatcher is included 
in this project.  To run it with Docker, run 

```
mvn clean install
docker compose --profile clientserver up -d
```

This will start a single-node Hazelcast pipeline running a string-reversing job and 
a Spring Boot front-end that accepts HTTP GET requests.  It can be invoked with 
a URL like: `http://localhost:8080/reverse?input=helloworld`. 

To stop everything run: `docker compose --profile clientserver down`

You can also run the example with the Hazelcast instance embedded in the web application: 

`docker compose --profile embedded up -d`.

# Usage

Include the following maven dependency in your project (replace VERSION with the version you wish to use).
```xml
<dependency>
    <groupId>hazelcast.platform.solutions</groupId>
    <artifactId>spring-hazelcast-pipeline-dispatcher</artifactId>
    <version>VERSION</version>
</dependency>
```

In your REST controller, add an autowired instance of `PipelineDispatcherFactory`.

```java
import hazelcast.platform.solutions.pipeline.dispatcher.PipelineDispatcherFactory;
...

@RestController
public class ExampleService {
    @Autowired
    PipelineDispatcherFactory pipelineDispatcherFactory;
    //...
}
```
To dispatch a request to the Hazelcast Pipeline use code similar to the following.

```java
    @GetMapping("/reverse")
    public DeferredResult<String> stringReverseService(@RequestParam String input){
        return pipelineDispatcherFactory.<String,String>dispatcherFor("reverse").send(input);
    }
```
The `dispatcherFor` method takes a pipeline name.  In the example above, the pipeline name is "reverse".  The 
request will be sent to a Hazelcast IMap named "reverse_request" and the response will be returned 
in a Hazelcast IMap named "reverse_response."  The pipeline implementation must use the "reverse_request" 
map as a source and the "reverse_response" map as a sink.

The `dispatcherFor` method has two type parameters.  The first is the type of the input and the second is the type 
of the output.  In this example, the input and output types are both Strings.  These should 
match with the types expected by and produced by the pipeline.

# Embedding a Hazelcast Instance 

A Hazelcast instance can be embedded into the application server by setting the `hazelcast.pipeline.dispatcher.embed_hazelcast` 
property to `true`. The `hazelcast.pipeline.dispatcher.hazelcast_config_file` property must point to an xml or yaml 
hazelcast server configuration file.  In this case, the application can deploy a pipeline using code 
similar to the following.
```java
@RestController
public class ExampleService {
    @Autowired
    PipelineDispatcherFactory pipelineDispatcherFactory;
    
    @Value("${hazelcast.pipeline.dispatcher.embed_hazelcast:false}")
    boolean embedHazelcast;
    
    // handler code here

    @PostConstruct
    public void init() {
        if (embedHazelcast) {
            HazelcastInstance hz = pipelineDispatcherFactory.getEmbeddedHazelcastInstance();
            Pipeline pipeline = ExamplePipeline.createPipeline("reverse_request", "reverse_response");
            hz.getJet().newJob(pipeline);
        }
    }
}
```

# Configuration

The pipeline dispatcher is configured using properties from the Spring Environment per the usual Spring Boot 
mechanism. See, for example, https://www.baeldung.com/properties-with-spring for more details. The properties 
used by the pipeline dispatcher are given below.

| Property                                            | Description                                                                                                              |
|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| hazelcast.pipeline.dispatcher.embed_hazelcast       | Whether to start a Hazelcast instance embedded in the application server (true) or connect to a remote instance (false). |
| hazelcast.pipeline.dispatcher.hazelcast_config_file | The configuration file used to initialize the hazelcast server (if embedded) or the hazelcast client (if not).           |
| hazelcast.pipeline.dispatcher.request_timeout_ms    | The number of milliseconds to wait for a response from the pipeline.  A timeout response will be returned if the response does not arrive after this amount of time. |



# Implementation Details
- This implementation uses an asynchronous architecture for high performance and scalability.  The REST controller's service
method returns a `DeferredResult` and retrieving the response from Hazelcast is also asynchronous.  When the response arrives,
the `setResult` method of the `DeferredResult` instance is called.
- The request is sent to the Pipeline by a `put` on a configurable request map.  The key is client id and a unique request id.
The value is just the request input.
- When the Pipeline has computed the result, it will put the response into a configurable response map.  The key will be the 
same as the key for the originating request.
- The Spring Boot application will use a listener with a predicate, containing its client id, to listen for relevant results.  
When a result with the matching client id is put into response map, the correct HTTP Server instance will be notified via its listener.
It will then use the unique id to look up the correct `DeferredResult` instance. The result will be sent to the original 
HTTP/REST client by calling `DeferredResult.setResult` method.

