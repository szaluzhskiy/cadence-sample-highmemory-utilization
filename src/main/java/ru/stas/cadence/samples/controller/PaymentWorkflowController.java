package ru.stas.cadence.samples.controller;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.stas.cadence.samples.propagator.TracingContextPropagator;
import ru.stas.cadence.samples.workflow.AwaitingWorkflow;
import ru.stas.cadence.samples.workflow.SampleConstants;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static ru.stas.cadence.samples.workflow.AwaitingWorkflow.TASK_LIST_AWAITING;

@RestController
public class PaymentWorkflowController {

  private WorkflowClient workflowClient;
  private WorkflowExecution workflowExecution;
  private List<AwaitingWorkflow.PaymentWorkflow> workflows = new CopyOnWriteArrayList<>();

  @PostConstruct
  private void init() {
    workflowClient = WorkflowClient.newInstance(
        "127.0.0.1",
        7933,
        SampleConstants.DOMAIN);
  }

  @PostMapping("/start")
  public void start10Workflows() {
    for (int i = 0; i < 10; i++) {
      WorkflowOptions options = new WorkflowOptions.Builder()
          .setExecutionStartToCloseTimeout(Duration.ofSeconds(600000))
          .setTaskList(TASK_LIST_AWAITING)
          .setContextPropagators(Collections.singletonList(new TracingContextPropagator()))
          .build();
      AwaitingWorkflow.PaymentWorkflow paymentWorkflow = workflowClient.newWorkflowStub(AwaitingWorkflow.PaymentWorkflow.class, options);
      workflowExecution = WorkflowClient.start(paymentWorkflow::payment, String.valueOf(i));
      workflows.add(paymentWorkflow);
    }
  }

  @PostMapping("/unlock")
  public void unlock1Workflow() {
    AwaitingWorkflow.PaymentWorkflow pw = workflows.remove(new Random().nextInt(workflows.size()));
    pw.unblock();
    }
}
