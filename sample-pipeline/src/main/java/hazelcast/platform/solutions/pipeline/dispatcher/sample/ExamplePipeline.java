package hazelcast.platform.solutions.pipeline.dispatcher.sample;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.pipeline.*;

import java.util.Map;

public class ExamplePipeline {
    public static void main(String[] args) {
        // event journal must be enabled on the request map but is not required for the response map
        Config hzConfig = new Config();
        hzConfig.getMapConfig("reverse_default_request").getEventJournalConfig().setEnabled(true);
        hzConfig.getJetConfig().setEnabled(true);

        // this will start daemon threads - the process will not exit
        HazelcastInstance hz = Hazelcast.newHazelcastInstance(hzConfig);
        Runtime.getRuntime().addShutdownHook(new Thread(hz::shutdown));

        hz.getJet().newJob(ExamplePipeline.createPipeline("reverse_default_request","reverse_response"));
    }

    static Pipeline createPipeline(String requestMapName, String responseMapName) {
        Pipeline pipeline = Pipeline.create();

        StreamStage<Map.Entry<String, String>> requestMapEntries =
                pipeline.<Map.Entry<String, String>>readFrom(
                        Sources.mapJournal(requestMapName, JournalInitialPosition.START_FROM_OLDEST))
                        .withIngestionTimestamps();

        requestMapEntries.writeTo(Sinks.logger( entry -> "Process Request: " + entry.getKey()));

        StreamStage<Tuple2<String, String>> reversedStrings = requestMapEntries.map(entry -> Tuple2.tuple2(
                entry.getKey(),
                new StringBuilder(entry.getValue()).reverse().toString()));

        reversedStrings.writeTo(Sinks.map(responseMapName));

        return pipeline;
    }
}
