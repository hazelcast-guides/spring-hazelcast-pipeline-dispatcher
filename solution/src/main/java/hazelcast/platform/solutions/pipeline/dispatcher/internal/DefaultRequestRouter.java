package hazelcast.platform.solutions.pipeline.dispatcher.internal;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import hazelcast.platform.solutions.pipeline.dispatcher.RequestRouter;

public class DefaultRequestRouter implements RequestRouter {

    IMap<String,Object> requestMap;

    public DefaultRequestRouter(HazelcastInstance hz, String name){
        requestMap = hz.getMap(name + "_request");
    }

    @Override
    public void send(String key, Object request) {
        requestMap.putAsync(key, request);
    }
}
