package com.hazelcast.solutions.pipeline.examples;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.solutions.pipeline.PipelineDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import javax.annotation.PostConstruct;
import java.util.Set;

@RestController
public class ExampleService  {
    @Autowired
    PipelineDispatcher pipelineDispatcher;

    @GetMapping("/reverse")
    public DeferredResult<String> stringReverseService(@RequestParam String input){
        return pipelineDispatcher.send(input);
    }

    // the code below is used to initialize an embedded pipeline for illustration purposes
    // none of it is required for typical usage

    @Value("${hazelcast.pipeline.dispatcher.embed_hazelcast:false}")
    boolean embedHazelcast;

    @Value("${hazelcast.pipeline.dispatcher.request_map}")
    private String requestMapName;

    @Value("${hazelcast.pipeline.dispatcher.response_map}")
    private String responseMapName;

    @PostConstruct
    public void init(){
        if (embedHazelcast){
            Set<HazelcastInstance> hzs = Hazelcast.getAllHazelcastInstances();
            HazelcastInstance hz = hzs.iterator().next();
            Pipeline pipeline = ExamplePipeline.createPipeline(requestMapName, responseMapName);
            hz.getJet().newJob(pipeline);
        }
    }


}
