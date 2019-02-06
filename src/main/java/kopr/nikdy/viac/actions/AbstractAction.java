package kopr.nikdy.viac.actions;

import com.google.gson.Gson;
import spark.Request;
import spark.Response;

import java.util.Objects;

public abstract class AbstractAction {

    private final Request request;
    private final Response response;

    protected AbstractAction(Request request, Response response) {
        this.request = request;
        this.response = response;
    }

    /**
     * Before sending response back to the client, response.wait() is called to wait for actor to finish processing.
     * This function releases the wait() allowing response to be sent.
     */
    public void markCompleted() {
        System.out.println("Completed");
        this.response.notify();
    }

    /**
     * Set response body to be sent back to the client.
     * Every response is serialized as JSON.
     *
     * @param content Content to add into response body
     */
    public void setResponseBody(Object content) {
        String responseBody = new Gson().toJson(content);
        this.response.body(responseBody);
    }

    public Request getRequest() {
        return request;
    }

    public Response getResponse() {
        return response;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractAction that = (AbstractAction) o;
        return Objects.equals(request, that.request) &&
                Objects.equals(response, that.response);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, response);
    }

    @Override
    public String toString() {
        return "AbstractAction{" +
                "request=" + request +
                ", response=" + response +
                '}';
    }

}
