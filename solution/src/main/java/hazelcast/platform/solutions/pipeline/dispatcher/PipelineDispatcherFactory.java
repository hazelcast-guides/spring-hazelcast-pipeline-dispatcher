package hazelcast.platform.solutions.pipeline.dispatcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.client.config.YamlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import hazelcast.platform.solutions.pipeline.dispatcher.internal.DefaultRequestRouter;
import hazelcast.platform.solutions.pipeline.dispatcher.internal.MultiVersionRequestRouter;
import hazelcast.platform.solutions.pipeline.dispatcher.internal.MultiVersionRequestRouterConfig;
import hazelcast.platform.solutions.pipeline.dispatcher.internal.RequestKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PipelineDispatcherFactory implements
        EntryAddedListener<String, Object>,
        EntryRemovedListener<String,Object>,
        EntryUpdatedListener<String,Object> {

    private static final Logger log = LoggerFactory.getLogger(PipelineDispatcherFactory.class);

    public static final String ROUTER_CONFIG_MAP = "router_config";

    @Value("${hazelcast.pipeline.dispatcher.embed_hazelcast:false}")
    private boolean embedHazelcast;

    // if hazelcast is embedded, provide a server config file, otherwise, provide a client config file
    @Value("${hazelcast.pipeline.dispatcher.hazelcast_config_file}")
    private String hazelcastConfigFile;

    // the maximum amount of time, in milliseconds to wait before returning a timeout error
    @Value("${hazelcast.pipeline.dispatcher.request_timeout_ms}")
    private long requestTimeoutMs;

    public <R,P> PipelineDispatcher<R,P> dispatcherFor(String name){
        PipelineDispatcher<R,P> result = dispatcherMap.computeIfAbsent(name, k -> {
            Object routerConfig = getRouterConfigFor(k);
            RequestRouter rr;
            if (routerConfig != null){
                // currently, the only type of router supported is the MultiVersionRequestRouter, so we assume here
                // that any entry in the ROUTER_CONFIG_MAP map is a MultiVersionRequestRouterConfig instance
                rr = new MultiVersionRequestRouter(k, (MultiVersionRequestRouterConfig) routerConfig);
            } else {
                rr = new DefaultRequestRouter(k);
            }
            return new PipelineDispatcher<R,P>(
                    this.requestKeyFactory,
                    hazelcastInstance,
                    name,
                    rr,
                    requestTimeoutMs);
        });


        return result;
    }

    private ConcurrentHashMap<String, PipelineDispatcher> dispatcherMap;

    private RequestKeyFactory requestKeyFactory;

    private HazelcastInstance hazelcastInstance;

    @PostConstruct
    public void initialize() {
        this.dispatcherMap = new ConcurrentHashMap<>();

        this.requestKeyFactory = new RequestKeyFactory();

        // create the hazelcast instance
        this.hazelcastInstance = HazelcastUtil.buildHazelcastInstance(hazelcastConfigFile, embedHazelcast);

        hazelcastInstance.getMap(ROUTER_CONFIG_MAP).addEntryListener(this, true);
    }

    /**
     * Retrieves the router configuration.  May return null.
     */
    private Object getRouterConfigFor(String name){
        return hazelcastInstance.getMap(ROUTER_CONFIG_MAP).get(name);
    }

    public HazelcastInstance getEmbeddedHazelcastInstance(){
        if (embedHazelcast)
            return hazelcastInstance;
        else
            return null;
    }

    @PreDestroy
    public void close(){
        hazelcastInstance.shutdown();
    }

    @Override
    public void entryAdded(EntryEvent<String, Object> event) {
        // again we are assuming that the only possible value type is MutliVersionRequestRouterConfig
        String name = event.getKey();
        handleAddUpdate(name, (MultiVersionRequestRouterConfig) event.getValue());
    }

    @Override
    public void entryUpdated(EntryEvent<String, Object> event) {
        // again we are assuming that the only possible value type is MutliVersionRequestRouterConfig
        String name = event.getKey();
        handleAddUpdate(name, (MultiVersionRequestRouterConfig) event.getValue());
    }

    @Override
    public void entryRemoved(EntryEvent<String, Object> event) {
        String name = event.getKey();
        handleRemove(name);
    }

    private <R,P> void handleAddUpdate(String name, MultiVersionRequestRouterConfig config){
        RequestRouter rr =  new MultiVersionRequestRouter(name, config);
        dispatcherMap.put(name,
                new PipelineDispatcher<R,P>(this.requestKeyFactory, hazelcastInstance, name, rr, requestTimeoutMs));

        log.info("Received routing update for \"" + name + "\" : " + config);
    }

    private <R,P> void handleRemove(String name){
        RequestRouter rr = new DefaultRequestRouter(name);
        dispatcherMap.put(name, new PipelineDispatcher<R,P>(this.requestKeyFactory, hazelcastInstance, name, rr, requestTimeoutMs));
        log.info("Set routing policy for \"" + name + "\" to default.");
    }
}
