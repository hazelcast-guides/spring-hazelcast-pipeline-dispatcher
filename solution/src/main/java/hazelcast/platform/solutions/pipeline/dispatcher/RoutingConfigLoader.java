package hazelcast.platform.solutions.pipeline.dispatcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import hazelcast.platform.solutions.pipeline.dispatcher.internal.MultiVersionRequestRouterConfig;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

public class RoutingConfigLoader {
    /**
     * Takes a JSON formatted dictionary of MultiVersionRequestRouterConfig instances and loads them.  The
     * dictionary key is the service name that will be loaded.
     *
     * The first command line argument is the path to a Hazelcast client configuration file (xml or yaml)
     * The second argument is the path to the file to load
     */
    public static void main(String []args){
        if (args.length != 2){
            System.err.println("Please provide 2 arguments.  " +
                    "The first is a path to the Hazelcast client configuration file " +
                    "and the second is the JSON file to load");
            System.exit(1);
        }

        String hazelcastConfigFile = args[0];
        String routingConfigFile = args[1];

        try {
            HazelcastInstance hz = HazelcastUtil.buildHazelcastInstance(hazelcastConfigFile, false);
            ObjectMapper mapper = new ObjectMapper();

            Map<String, MultiVersionRequestRouterConfig> configMap = mapper.readValue(
                    new File(routingConfigFile),
                    new TypeReference<>() {}
            );

            for(MultiVersionRequestRouterConfig config: configMap.values()) config.check();

            IMap<String,MultiVersionRequestRouterConfig> remoteConfigMap =
                    hz.getMap(PipelineDispatcherFactory.ROUTER_CONFIG_MAP);

            remoteConfigMap.putAll(configMap);

            System.out.println("Loaded " + configMap.size() + " configuration entries");

            hz.shutdown();
        } catch(Exception rx){
            System.err.println("An error occurred. Program will exit.");
            rx.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
