package hazelcast.platform.solutions.pipeline.dispatcher.internal;

import hazelcast.platform.solutions.pipeline.dispatcher.RequestRouter;

public class DefaultRequestRouter implements RequestRouter {

    private final  String requestMapName;
    public DefaultRequestRouter(String name){
        this.requestMapName = name + "_default_request";
    }

    @Override
    public String getRequestMapName() {
        return requestMapName;
    }
}
