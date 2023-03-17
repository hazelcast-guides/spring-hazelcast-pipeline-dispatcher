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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class HazelcastUtil {
    /**
     * Creates an embedded or client HazelcastInstance based on an XML or YAML configuration file.
     * Throws a RuntimeException if the configuration file doesn't exist or isn't parseable.
     *
     * @param configFileName
     * @param embedded
     * @return the HazelcastInstance
     */
    public static HazelcastInstance buildHazelcastInstance(String hazelcastConfigFile, boolean embedded){
        HazelcastInstance result;
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

        if (embedded) {
            try {
                Config config = isXML ?
                        new XmlConfigBuilder(hazelcastConfigFile).build()
                        : new YamlConfigBuilder(hazelcastConfigFile).build();
                result = Hazelcast.newHazelcastInstance(config);
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
                result = HazelcastClient.newHazelcastClient(config);
            } catch (IOException iox) {
                throw new RuntimeException(
                        "An error occurred while attempting to read file: \"" + hazelcastConfigFile + "\".", iox);
            }
        }

        return result;
    }
}
