package hazelcast.platform.solutions.pipeline.dispatcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import hazelcast.platform.solutions.pipeline.dispatcher.internal.MultiVersionRequestRouterConfig;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoutingConfigTool {
    /**
     * usage: RoutingConfigTool [-h] --hz-cluster-name HZ_CLUSTER_NAME
     *                            --hz-servers HZ_SERVERS [HZ_SERVERS ...]
     *                            [--input INPUT] {dump,load}
     *
     * Inspect and maintain routing configuration
     *
     * positional arguments:
     *   {dump,load}            specifies the desired action
     *
     * named arguments:
     *   -h, --help             show this help message and exit
     *   --hz-cluster-name HZ_CLUSTER_NAME
     *                          the  name  of  the  Hazelcast  cluster  where  the
     *                          configuration resides
     *   --hz-servers HZ_SERVERS [HZ_SERVERS ...]
     *                          one or more  Hazelcast  cluster members, specified
     *                          in host[:port] format
     *   --input INPUT          The JSON file containing the control data to load
     *
     *   Sample File Format
     *   {
     *   "serviceA": {
     *       "versions" : ["v1","v2"],
     *       "percentages" : [0.5, 1.0]
     *     },
     *   "serviceB": {
     *       "versions" : ["v1","v2"],
     *       "percentages" : [0.9, 1.0]
     *     }
     *   }
     *
     *   Note that the percentages list must be the same length as the versions list and
     *   all percentages must be in [0.0,1.0] with each one greater than the previous one.
     *
     *   To select the version used, a random number in [0.0, 1.0] is generated.  The percentages are
     *   evaluated in order and the version is used that corresponds to the first percentage that is
     *   greater than or equal to the random number.  For example, for "serviceB" defined above,
     *   a random number of .9 would cause "v1" to be used while a random number of .95 would cause "v2" to be used.
     */
    public static void main(String []args){
        ArgumentParser parser = ArgumentParsers.newFor("RoutingConfigTool").build().defaultHelp(true)
                .description("Inspect and maintain routing configuration");

        parser.addArgument("action").choices("dump", "load").required(true).help("specifies the desired action");
        parser.addArgument("--hz-cluster-name").required(true).type(String.class).help("the name of the Hazelcast cluster where the configuration resides");
        parser.addArgument("--hz-servers").required(true).nargs("+").type(String.class).help("one or more Hazelcast cluster members, specified in host[:port] format");
        parser.addArgument("--input").type(String.class).required(false).help("The JSON file containing the control data to load");

        Namespace arguments = null;
        try {
            arguments = parser.parseArgs(args);
        } catch (ArgumentParserException x){
            parser.handleError(x);
            System.exit(1);
        }

        String action = arguments.getString("action");
        String clusterName = arguments.getString("hz_cluster_name");
        List<String> servers = arguments.getList("hz_servers");
        String fileName = arguments.getString("input");

        if (action.equals("load") && fileName == null){
            System.err.println("--input argument must be specified if the action is \"load\".");
            System.exit(1);
        }

        /*
         * Since the dump command dumps configuration to System.out, all other output will be sent to
         * System.err so it will be possible to get uncorrupted output by redirection only System.out.
         * For example: RoutingConfigTool --hz-cluster-name=dev --hz-servers=hz:5701 dump 1> output.json
         */
        try {
            ClientConfig clientConfig = new ClientConfig();
            clientConfig.setClusterName(clusterName);
            for(String serverName: servers){
                clientConfig.getNetworkConfig().addAddress(serverName);
            }
            clientConfig.getConnectionStrategyConfig().setAsyncStart(false);

            HazelcastInstance hz = HazelcastClient.newHazelcastClient(clientConfig);
            System.err.println("Connected");

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            if (action.equals("load")) {
                Map<String, MultiVersionRequestRouterConfig> configMap = mapper.readValue(
                        new File(fileName),
                        new TypeReference<>() {
                        }
                );

                for (MultiVersionRequestRouterConfig config : configMap.values()) config.check();

                IMap<String, MultiVersionRequestRouterConfig> remoteConfigMap =
                        hz.getMap(PipelineDispatcherFactory.ROUTER_CONFIG_MAP);

                remoteConfigMap.putAll(configMap);

                System.err.println("Loaded " + configMap.size() + " configuration entries");
            } else {
                IMap<String, MultiVersionRequestRouterConfig> remoteConfigMap =
                        hz.getMap(PipelineDispatcherFactory.ROUTER_CONFIG_MAP);

                Map<String, MultiVersionRequestRouterConfig> localMap = new HashMap<>();
                for (Map.Entry<String, MultiVersionRequestRouterConfig> next : remoteConfigMap) {
                    localMap.put(next.getKey(), next.getValue());
                }

                mapper.writeValue(System.out, localMap);
            }

            hz.shutdown();
        } catch(Exception rx){
            System.err.println("An error occurred. Program will exit.");
            rx.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
