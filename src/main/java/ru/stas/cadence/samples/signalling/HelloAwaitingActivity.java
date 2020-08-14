package ru.stas.cadence.samples.signalling;

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.workflow.QueryMethod;
import com.uber.cadence.workflow.SignalMethod;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

public class HelloAwaitingActivity {

  public static final String TASK_LIST = "HelloAwaitingActivity";

  /** Workflow interface has to have at least one method annotated with @WorkflowMethod. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 6000, taskList = TASK_LIST)
    String getGreeting(int orderId, String name);

    @SignalMethod
    void unlockSignal();

    @QueryMethod
    int getOrderId();
  }

  /** Activity interface is just a POJI. */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 600)
    String composeGreeting(String greeting, String name);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {
    private boolean unlock = false;
    private int id;

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final GreetingActivities activities =
        Workflow.newActivityStub(GreetingActivities.class);

    @Override
    public String getGreeting(int orderId, String name) {
      this.id = orderId;
      System.out.println("Start getGreeting - " + orderId);
      // This is a blocking call that returns only after the activity has completed.
      System.out.println("LOCK  order - " + orderId);
      Workflow.await(() -> unlock);
      System.out.println("UNLOCK order - " + orderId);
      String res = activities.composeGreeting("Hello", name);
      System.out.println("Stop getGreeting - " + orderId);
      return res;
    }

    @Override
    public void unlockSignal() {
      unlock = true;
    }

    @Override
    public int getOrderId() {
      return id;
    }
  }

  public static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public String composeGreeting(String greeting, String name) {
      return greeting + " " + name + "!";
    }
  }
}
