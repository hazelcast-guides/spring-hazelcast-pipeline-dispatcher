package hazelcast.platform.solutions.pipeline.dispatcher;

/**
 * The RequestRouter is responsible for actually selecting the map that will receive the request.
 * This abstraction enables the possibility of sending to different maps and therefore different service implementation
 * Pipelines, based on configuration.
 * @param <R>
 */
public interface RequestRouter<R> {
    void send(String key, R request);
}
