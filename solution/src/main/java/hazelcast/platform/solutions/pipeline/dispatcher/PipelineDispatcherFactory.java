package hazelcast.platform.solutions.pipeline.dispatcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.client.config.YamlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import hazelcast.platform.solutions.pipeline.dispatcher.internal.DefaultRequestRouter;
import hazelcast.platform.solutions.pipeline.dispatcher.internal.RequestKeyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PipelineDispatcherFactory {
    @Value("${hazelcast.pipeline.dispatcher.embed_hazelcast:false}")
    private boolean embedHazelcast;

    // if hazelcast is embedded, provide a server config file, otherwise, provide a client config file
    @Value("${hazelcast.pipeline.dispatcher.hazelcast_config_file}")
    private String hazelcastConfigFile;

    // the maximum amount of time, in milliseconds to wait before returning a timeout error
    @Value("${hazelcast.pipeline.dispatcher.request_timeout_ms}")
    private long requestTimeoutMs;

    public <R,P> PipelineDispatcher<R,P> dispatcherFor(String name){
        PipelineDispatcher<R,P> result = dispatcherMap.computeIfAbsent(name, k ->
            new PipelineDispatcher<R,P>(
                this.requestKeyFactory,
                    new DefaultRequestRouter<>(hazelcastInstance, k),
                this.hazelcastInstance.getMap(k + "_response"),
                requestTimeoutMs));

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
        File configFile = new File(hazelcastConfigFile);
        if (!configFile.exists()) {
            throw new RuntimeException("Required configuration file \"" + configFile + "\" not found.");
        }
        if (!configFile.canRead()) {
            throw new RuntimeException("Cannot read Hazelcast configuration file:\" " + configFile + "\"");
        }

        boolean isXML = hazelcastConfigFile.endsWith(".xml");
        if (!isXML) {
            if (!hazelcastConfigFile.endsWith(".yaml") && !hazelcastConfigFile.endsWith(".yml")) {
                throw new RuntimeException("Hazelcast configuration file name must end with \".xml\", \".yml\" or \".yaml\".");
            }
        }

        if (embedHazelcast) {
            try {
                Config config = isXML ?
                        new XmlConfigBuilder(hazelcastConfigFile).build()
                        : new YamlConfigBuilder(hazelcastConfigFile).build();
                this.hazelcastInstance = Hazelcast.newHazelcastInstance(config);
            } catch (FileNotFoundException nfx) {
                // this should never happen since we've already checked for the existence of this file
                throw new RuntimeException(
                        "Could not find required configuration file: \"" + hazelcastConfigFile + "\"");
            }
        } else {
            try {
                ClientConfig config = isXML ?
                        new XmlClientConfigBuilder(configFile).build() :
                        new YamlClientConfigBuilder(configFile).build();
                this.hazelcastInstance = HazelcastClient.newHazelcastClient(config);
            } catch (IOException iox) {
                throw new RuntimeException(
                        "An error occurred while attempting to read file: \"" + hazelcastConfigFile + "\".", iox);
            }
        }
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
}
