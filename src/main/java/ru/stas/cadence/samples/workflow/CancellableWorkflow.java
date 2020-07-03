package ru.stas.cadence.samples.workflow;

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.*;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CancellableWorkflow {
  /**
   * Hello World Cadence workflow that executes a single activity. Requires a local instance the
   * Cadence service to be running.
   */

  static final String TASK_LIST = "HelloActivity";
  private static final String DOMAIN = "sample";

  /**
   * Workflow interface has to have at least one method annotated with @WorkflowMethod.
   */
  public interface GreetingWorkflow {
    /**
     * @return greeting string
     */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 240000, taskList = TASK_LIST)
    String getGreeting(String name);
  }

  /**
   * Activity interface is just a POJO.
   */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 120)
    String composeGreeting(String greeting, String name);
  }

  /**
   * GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting.
   */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final GreetingActivities activities =
        Workflow.newActivityStub(GreetingActivities.class);

    @Override
    public String getGreeting(String name) {
      String result;
      // This is a blocking call that returns only after the activity has completed.
      CompletablePromise<String> activityPromise = Workflow.newPromise();
       CancellationScope scope = Workflow.newCancellationScope(() -> {
         activityPromise.completeFrom(Async.function(activities::composeGreeting, "Hello", name));
        });
       scope.run(); // returns immediately as the activities are invoked asynchronously
    //  Async.function(activities::composeGreetingOuter, "composeGreetingOuter");
      try {
        activityPromise.get(5, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        scope.cancel();
        System.out.println("Performing compensation steps");
      }
      return "";
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public String composeGreeting(String greeting, String name) {
      try {
        CloseableHttpClient httpclient = HttpClients.createDefault();

        HttpPost post = new HttpPost("http://127.0.0.1:8099/longoperation");
        try {
          System.out.println("Activity composeGreeting. Send long request");
          httpclient.execute(post);
        } catch (IOException e) {
          System.out.println("Activity composeGreeting. Error");
          throw new RuntimeException(e);
        }
      } catch (CancellationException e) {
        System.out.println("Activity composeGreeting. Cancelled");
        throw e;
      }
      return greeting + " " + name + "!";
    }
  }

  public static void main(String[] args) {
    // Start a worker that hosts both workflow and activity implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    System.out.println(greeting);
    System.exit(0);
  }

}
