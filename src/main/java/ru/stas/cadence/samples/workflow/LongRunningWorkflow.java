package ru.stas.cadence.samples.workflow;

import com.uber.cadence.DomainAlreadyExistsError;
import com.uber.cadence.RegisterDomainRequest;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import static ru.stas.cadence.samples.workflow.SampleConstants.DOMAIN;

@Slf4j
@Component
public class LongRunningWorkflow implements ApplicationRunner {

  public static final String TASK_LIST_MAIN = "hello_long_run";

  public void run(ApplicationArguments args) {
    registerDomain();
    startFactory();
  }

  private void startFactory() {
    // Start a worker that hosts both parent and child workflow implementations.
    Scope scope =
        new RootScopeBuilder()
            .reporter(new CustomCadenceClientStatsReporter())
            .reportEvery(Duration.ofSeconds(1));

    Worker.Factory factory =
        new Worker.Factory(
            "127.0.0.1",
            7933,
            DOMAIN,
            new Worker.FactoryOptions.Builder().setMetricScope(scope).setDisableStickyExecution(true).build());

    Worker workerParent =
        factory.newWorker(
            TASK_LIST_MAIN, new WorkerOptions.Builder().setMetricsScope(scope).build()
        );

    workerParent.registerWorkflowImplementationTypes(LongWorkflowImpl.class);
    workerParent.registerActivitiesImplementations(new ActivitiesImpl());

    // Start listening to the workflow and activity task lists.
    factory.start();
  }

  private void registerDomain() {
    IWorkflowService cadenceService = new WorkflowServiceTChannel();
    RegisterDomainRequest request = new RegisterDomainRequest();
    request.setDescription("Java Samples");
    request.setEmitMetric(false);
    request.setName(DOMAIN);
    int retentionPeriodInDays = 1;
    request.setWorkflowExecutionRetentionPeriodInDays(retentionPeriodInDays);
    try {
      cadenceService.RegisterDomain(request);
      System.out.println(
          "Successfully registered domain \""
              + DOMAIN
              + "\" with retentionDays="
              + retentionPeriodInDays);

    } catch (DomainAlreadyExistsError e) {
      log.error("Domain \"" + DOMAIN + "\" is already registered");

    } catch (TException e) {
      log.error("Error occurred", e);
    }
  }

  /**
   * The parent workflow interface.
   */
  public interface LongWorkflow {
    /**
     * @return greeting string
     */
    @WorkflowMethod(taskList = TASK_LIST_MAIN)
    String process(String name);
  }

  public interface Activities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 3600*3600)
    String compose(char[] array);
  }

  static class ActivitiesImpl implements Activities {

    @Override
    public String compose(char[] array) {
      log.info("compose activity");
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return "";
    }
  }

  public static class LongWorkflowImpl implements LongWorkflow {

    @Override
    public String process(String name) {
      log.debug("START WF");
      // Do something else here.
      String parentPromises = runParentActivities();

      log.debug("Got result in parent workflow");
      Workflow.await(() -> false);
      return ""; // blocks waiting for the child to complete.
    }

    private String runParentActivities() {
      ActivityOptions ao =
          new ActivityOptions.Builder()
              .setTaskList(TASK_LIST_MAIN)
              .build();

      char[] data = new char[1024*10];
      int step = 0;
      while (true) {
        step += 1;
        log.info("run activity " + step % 100);
        Activities activity = Workflow.newActivityStub(Activities.class, ao);
        activity.compose(data);
      }
    }
  }
}
