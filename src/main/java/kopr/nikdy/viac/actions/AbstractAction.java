package kopr.nikdy.viac.actions;

import com.google.gson.Gson;
import spark.Request;
import spark.Response;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public abstract class AbstractAction {

    private final Request request;
    private final Response response;

    /**
     * Latch waiting for all tasks to be processed before sending response to the client.
     * Usually is set to 1 task.
     */
    private final CountDownLatch pendingTasks;

    protected AbstractAction(Request request, Response response, CountDownLatch pendingTasks) {
        this.request = request;
        this.response = response;
        this.pendingTasks = pendingTasks;
    }

    /**
     * Before sending response back to the client, response.wait() is called to wait for actor to finish processing.
     * This function releases the wait() allowing response to be sent.
     */
    public void markCompleted() {
        this.pendingTasks.countDown();
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

    public CountDownLatch getPendingTasks() {
        return pendingTasks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractAction that = (AbstractAction) o;
        return Objects.equals(request, that.request) &&
                Objects.equals(response, that.response) &&
                Objects.equals(pendingTasks, that.pendingTasks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, response, pendingTasks);
    }

    @Override
    public String toString() {
        return "AbstractAction{" +
                "request=" + request +
                ", response=" + response +
                ", pendingTasks=" + pendingTasks +
                '}';
    }

}
