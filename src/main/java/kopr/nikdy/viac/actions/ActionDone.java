package kopr.nikdy.viac.actions;

import spark.Request;
import spark.Response;

import java.util.concurrent.CountDownLatch;

public class ActionDone extends Action {

    public ActionDone(Action action) {
        this(action.getRequest(), action.getResponse(), action.getPendingTasks());
    }

    protected ActionDone(Request request, Response response, CountDownLatch pendingTasks) {
        super(request, response, pendingTasks);
    }

}
