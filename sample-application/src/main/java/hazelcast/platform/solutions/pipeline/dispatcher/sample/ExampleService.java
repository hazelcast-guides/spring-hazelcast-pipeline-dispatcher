package hazelcast.platform.solutions.pipeline.dispatcher.sample;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.pipeline.Pipeline;
import hazelcast.platform.solutions.pipeline.dispatcher.PipelineDispatcherFactory;
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
    PipelineDispatcherFactory pipelineDispatcherFactory;

    @GetMapping("/reverse")
    public DeferredResult<String> stringReverseService(@RequestParam String input){
        return pipelineDispatcherFactory.<String,String>dispatcherFor("reverse").send(input);
    }

    // the code below is used to initialize an embedded pipeline for illustration purposes
    // none of it is required for typical usage

    @Value("${hazelcast.pipeline.dispatcher.embed_hazelcast:false}")
    boolean embedHazelcast;

    @PostConstruct
    public void init(){
        if (embedHazelcast){
            HazelcastInstance hz = pipelineDispatcherFactory.getEmbeddedHazelcastInstance();
            Pipeline pipeline = ExamplePipeline.createPipeline("reverse_request", "reverse_response");
            hz.getJet().newJob(pipeline);
        }
    }


}
