package ru.stas.cadence.samples.controller;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowExecutionInfo;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.internal.common.WorkflowExecutionUtils;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.stas.cadence.samples.LocalActivityWorkflow.Hello;
import ru.stas.cadence.samples.propagator.TracingContextPropagator;
import ru.stas.cadence.samples.workflow.CancellableWorkflow;
import ru.stas.cadence.samples.workflow.ParentWorkflow;
import ru.stas.cadence.samples.workflow.SampleConstants;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import static ru.stas.cadence.samples.workflow.SampleConstants.TASK_LIST_MAIN;

@Slf4j
@RestController
public class WorkflowController {

  private WorkflowClient workflowClient;
  private WorkflowExecution workflowExecution;
  private Optional<IWorkflowService> ws;

  @PostConstruct
  private void init() {
    workflowClient = WorkflowClient.newInstance(
        "127.0.0.1",
        7933,
        SampleConstants.DOMAIN);
  }

  @GetMapping("/start/wf")
  private void startWF() {
    log.debug("start wf");
    WorkflowOptions options = new WorkflowOptions.Builder()
        .setExecutionStartToCloseTimeout(Duration.ofSeconds(60))
        .setTaskList(TASK_LIST_MAIN)
        .setContextPropagators(Collections.singletonList(new TracingContextPropagator()))
        .build();
    ParentWorkflow.GreetingWorkflow parentWorkflow = workflowClient.newWorkflowStub(ParentWorkflow.GreetingWorkflow.class, options);
    workflowExecution = WorkflowClient.start(parentWorkflow::getGreeting, "NewWorld");
  }

  @GetMapping("/start/cancellable/wf")
  private void startCancallable() {
    log.debug("start wf cancellable");
    WorkflowOptions options = new WorkflowOptions.Builder()
        .setExecutionStartToCloseTimeout(Duration.ofSeconds(600))
        .setTaskList(TASK_LIST_MAIN)
        .build();
    CancellableWorkflow.GreetingWorkflow workflow = workflowClient.newWorkflowStub(CancellableWorkflow.GreetingWorkflow.class, options);
    WorkflowExecution we = WorkflowClient.start(workflow::getGreeting, "NewWorld");
  }

  @GetMapping("/wf/es")
  private void getWorkflowExecutionStatus() {
    if (ws == null || !ws.isPresent()) {
      ws = Optional.ofNullable(new WorkflowServiceTChannel("localhost", 7933));
    }
    WorkflowExecutionInfo wei = WorkflowExecutionUtils.describeWorkflowInstance (ws.get(),"sample", workflowExecution);
    System.out.println("close status " + wei.closeStatus);
  }
}
