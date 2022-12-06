# Overview

This connector enables the use of Hazelcast Pipelines to service HTTP Requests.  This approach separates the 
HTTP Connection handling and the response computation.  A regular Spring Boot application handles the HTTP connection 
and the Hazelcast cluster, running a Pipeline, handles the response computation.  

Some advantages of this approach are:
- The Spring Boot application is very light and stateless.  It is easy to scale simply by running multiple instances behind a load balancer.
- The Spring Boot application servers contain no business logic or data, making them safer to run in an internet facing subnet.
- The business logic is in an independently deployable Hazelcast pipeline.  The logic can be updated by deploying a new Pipeline, without touching the web servers.
- The web tier and the business logic tiers scale independently.

# Usage


# Implementation Notes
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

