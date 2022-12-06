package hazelcast.platform.solutions.pipeline.dispatcher;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;
import com.hazelcast.query.Predicates;
import hazelcast.platform.solutions.pipeline.dispatcher.internal.RequestKey;
import hazelcast.platform.solutions.pipeline.dispatcher.internal.RequestKeyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.ConcurrentHashMap;

/**
 * PipelineDispatcher is designed to be a singleton scoped bean
 */

public class PipelineDispatcher<R,P> implements EntryAddedListener<RequestKey,P> {
    RequestKeyFactory requestKeyFactory;

    private final String clientId;
    private final IMap<RequestKey, R> requestMap;

    private final ConcurrentHashMap<RequestKey, DeferredResult<P>> pendingRequestMap;

    private final long requestTimeoutMs;
    public PipelineDispatcher(
            RequestKeyFactory requestKeyFactory,
            IMap<RequestKey, R> requestMap,
            IMap<RequestKey, P> responseMap,
            long requestTimeoutMs){
        this.requestTimeoutMs = requestTimeoutMs;
        this.requestKeyFactory = requestKeyFactory;
        this.pendingRequestMap = new ConcurrentHashMap<>();

        this.clientId = requestKeyFactory.newRandomClientId();
        this.requestMap = requestMap;

        PredicateBuilder.EntryObject entry = Predicates.newPredicateBuilder().getEntryObject();
        Predicate<RequestKey, P> myRequests = entry.key().get("clientId").equal(clientId);
        responseMap.addEntryListener(this, myRequests, true);
    }

    @Override
    public void entryAdded(EntryEvent<RequestKey, P> entryEvent) {
        DeferredResult<P> result = pendingRequestMap.get(entryEvent.getKey());
        if (result != null){
            result.setResult(entryEvent.getValue());
        } else {
            // TODO - probably should use a logger
            System.err.println("WARNING: Could not find a pending request for " + entryEvent.getKey());
        }
    }

    public DeferredResult<P> send(R  request){
        RequestKey key = requestKeyFactory.newRequestKey(this.clientId);
        DeferredResult<P> result = new DeferredResult<>(requestTimeoutMs);
        result.onTimeout(() -> result.setErrorResult(
                ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Request timeout occurred.")));
        pendingRequestMap.put(key, result);
        requestMap.putAsync(key, request);
        return result;
    }
}
