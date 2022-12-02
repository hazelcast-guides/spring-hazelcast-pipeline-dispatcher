package com.hazelcast.solutions.pipeline;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.client.config.YamlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.solutions.pipeline.internal.RequestKeyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

@Configuration
public class PipelineDispatcherFactory {

    @Value("${hazelcast.pipeline.dispatcher.request_map}")
    private String requestMapName;

    @Value("${hazelcast.pipeline.dispatcher.response_map}")
    private String responseMapName;

    @Value("${hazelcast.pipeline.dispatcher.embed_hazelcast:false}")
    private boolean embedHazelcast;

    // if hazelcast is embedded, provide a server config file, otherwise, provide a client config file
    @Value("${hazelcast.pipeline.dispatcher.hazelcast_config_file}")
    private String hazelcastConfigFile;

    // the maximum amount of time, in milliseconds to wait before returning a timeout error
    @Value("${hazelcast.pipeline.dispatcher.request_timeout_ms}")
    private long requestTimeoutMs;

    @Bean
    @Scope("singleton")
    PipelineDispatcher pipelineDispatcher(){

        PipelineDispatcher result = new PipelineDispatcher(
                this.requestKeyFactory,
                this.hazelcastInstance.getMap(requestMapName),
                this.hazelcastInstance.getMap(responseMapName),
                requestTimeoutMs);

        return result;
    }
    private RequestKeyFactory requestKeyFactory;
    private HazelcastInstance hazelcastInstance;

    @PostConstruct
    public void initialize() {
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

    @PreDestroy
    public void close(){
        hazelcastInstance.shutdown();
    }
}
