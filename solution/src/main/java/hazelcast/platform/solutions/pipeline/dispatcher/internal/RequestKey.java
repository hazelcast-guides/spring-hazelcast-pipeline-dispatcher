package hazelcast.platform.solutions.pipeline.dispatcher.internal;

import java.io.Serializable;
import java.util.Objects;

public class RequestKey implements Serializable {
    private String clientId;
    private Long requestId;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestKey that = (RequestKey) o;
        return clientId.equals(that.clientId) && requestId.equals(that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, requestId);
    }

    @Override
    public String toString() {
        return "RequestKey{" +
                "clientId='" + clientId + '\'' +
                ", requestId=" + requestId +
                '}';
    }
}
