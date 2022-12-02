package com.hazelcast.solutions.pipeline;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;
import com.hazelcast.query.Predicates;
import com.hazelcast.solutions.pipeline.internal.RequestKey;
import com.hazelcast.solutions.pipeline.internal.RequestKeyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.ConcurrentHashMap;

/**
 * PipelineDispatcher is designed to be a singleton scoped bean
 */

public class PipelineDispatcher implements EntryAddedListener<RequestKey,String> {
    RequestKeyFactory requestKeyFactory;

    private final String clientId;
    private final IMap<RequestKey, String> requestMap;

    private final ConcurrentHashMap<RequestKey, DeferredResult<String>> pendingRequestMap;

    private final long requestTimeoutMs;
    public PipelineDispatcher(
            RequestKeyFactory requestKeyFactory,
            IMap<RequestKey, String> requestMap,
            IMap<RequestKey, String> responseMap,
            long requestTimeoutMs){
        this.requestTimeoutMs = requestTimeoutMs;
        this.requestKeyFactory = requestKeyFactory;
        this.pendingRequestMap = new ConcurrentHashMap<>();

        this.clientId = requestKeyFactory.newRandomClientId();
        this.requestMap = requestMap;

        PredicateBuilder.EntryObject entry = Predicates.newPredicateBuilder().getEntryObject();
        Predicate<RequestKey, String> myRequests = entry.key().get("clientId").equal(clientId);
        responseMap.addEntryListener(this, myRequests, true);
    }

    @Override
    public void entryAdded(EntryEvent<RequestKey, String> entryEvent) {
        DeferredResult<String> result = pendingRequestMap.get(entryEvent.getKey());
        if (result != null){
            result.setResult(entryEvent.getValue());
        } else {
            // TODO - probably should use a logger
            System.err.println("WARNING: Could not find a pending request for " + entryEvent.getKey());
        }
    }

    public DeferredResult<String> send(String  request){
        RequestKey key = requestKeyFactory.newRequestKey(this.clientId);
        DeferredResult<String> result = new DeferredResult<>(requestTimeoutMs);
        result.onTimeout(() -> result.setErrorResult(
                ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Request timeout occurred.")));
        pendingRequestMap.put(key, result);
        requestMap.putAsync(key, request);
        return result;
    }
}
