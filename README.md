# Overview

This connector enables the use of Hazelcast Pipelines to service HTTP Requests.  One major benefit of this approach is 
the ability to have multiple implementations of a service and control the amount of traffic directed to each, enabling 
blue/green style deployments.

Some additional advantages of this approach are:
- The Spring Boot application is very light and stateless.  It is easy to scale simply by running multiple instances behind a load balancer.
- The Spring Boot application servers contain no business logic or data, making them safer to run in an internet facing subnet.
- The business logic is in an independently deployable Hazelcast pipeline.  The logic can be updated by deploying a new Pipeline, without touching the web servers.
- The web tier and the business logic tiers scale independently.
- Compute resources do not have to be specifically assigned to each service.  Instead, all service implementations 
  share the compute resources of a Hazelcast cluster.  Adding compute capacity is as easy as adding servers to the 
  cluster.

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

Both the "embedded" profile and the "clientserver" profile start a Hazelcast Management Center, which can be accessed 
at `http://localhost:8888`.

# Basic Usage

This section covers the single implementation per service scenario.  Blue/Green deployment is covered in a later 
section. Using Blue/Green deployment requires additional configuration on the Hazelcast cluster but does not change the 
web service at all.  

## The Web Service

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
Dispatch a request to Hazelcast using code similar to the following.

```java
    @GetMapping("/reverse")
    public DeferredResult<String> stringReverseService(@RequestParam String input){
        return pipelineDispatcherFactory.<String,String>dispatcherFor("reverse").send(input);
    }
```
The *dispatcherFor* method takes a service name.  In the example above, the service name is *reverse*.  

The *dispatcherFor* method has two type parameters.  The first is the type of the input and the second is the type 
of the output.  In this example, the input and output types are both Strings.  These should 
match with the types expected by and produced by the pipeline that implements the service.


### Configuring the Connection to Hazelcast

The pipeline dispatcher is configured using properties from the Spring Environment per the usual Spring Boot
mechanism. See, for example, https://www.baeldung.com/properties-with-spring for more details. The properties
used by the pipeline dispatcher are given below.

| Property                                            | Description                                                                                                              |
|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| hazelcast.pipeline.dispatcher.embed_hazelcast       | Whether to start a Hazelcast instance embedded in the application server (true) or connect to a remote instance (false). |
| hazelcast.pipeline.dispatcher.hazelcast_config_file | The configuration file used to initialize the hazelcast server (if embedded) or the hazelcast client (if not).           |
| hazelcast.pipeline.dispatcher.request_timeout_ms    | The number of milliseconds to wait for a response from the pipeline.  A timeout response will be returned if the response does not arrive after this amount of time. |


# Implementing the Service with a Hazelcast Pipeline

Pipelines that implement services must follow these guidelines.  
1. It  must read the request from an *IMap* backed *StreamSource* created using *Sources.mapJournal*
2. It must write the response to an *IMap* backed *Sink* created using *Sinks.map*. 
3. The request and response types must be serializable and must correspond to the types declared by the corresponding 
   *PipelineDispatcher*
4. The map names must follow a certain convention, which is described below.

See *hazelcast.platform.solutions.pipeline.dispatcher.sample.ExamplePipeline* for an example.

## Map Naming Conventions

By default, the name of the input map is *SERVICE_NAME_request* and the output map is *SERVICE_NAME_response*. For 
example, for a service named *reverse*, the input and output maps would be, respectively, *reverse_request* and 
*reverse_response*.  

However, it is also possible to run multiple versions of a service at the same time and to route traffic between them.
If multi-version support is enabled (see below) then the name of the input map changes to include the version name.
Version names can be any simple string.  For example, if you wish to deploy to version of the *reverse* service, say 
*v1* and *v2* then the corresponding input map names would be *reverse_v1_request* and *reverse_v2_request*.  

> Note that the output map is not version specific.  both pipelines would  write their output to 
> the *reverse_response* map.  

# Embedding a Hazelcast Instance 

A Hazelcast instance can be embedded into the application server by setting the *hazelcast.pipeline.dispatcher.embed_hazelcast* 
property to *true*. The *hazelcast.pipeline.dispatcher.hazelcast_config_file* property must point to an xml or yaml 
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

