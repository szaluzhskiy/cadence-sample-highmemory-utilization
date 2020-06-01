package ru.stas.cadence.samples.controller;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.stas.cadence.samples.propagator.TracingContextPropagator;
import ru.stas.cadence.samples.workflow.ParentWorkflow;
import ru.stas.cadence.samples.workflow.SampleConstants;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;

import static ru.stas.cadence.samples.workflow.SampleConstants.TASK_LIST_MAIN;

@Slf4j
@RestController
public class WorkflowController {

  private WorkflowClient workflowClient;

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
        .setExecutionStartToCloseTimeout(Duration.ofSeconds(5))
        .setTaskList(TASK_LIST_MAIN)
        .setContextPropagators(Collections.singletonList(new TracingContextPropagator()))
        .build();
    ParentWorkflow.GreetingWorkflow parentWorkflow = workflowClient.newWorkflowStub(ParentWorkflow.GreetingWorkflow.class, options);
    WorkflowExecution we = WorkflowClient.start(parentWorkflow::getGreeting, "World");
  }
}
