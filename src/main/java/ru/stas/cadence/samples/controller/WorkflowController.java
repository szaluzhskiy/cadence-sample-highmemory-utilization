package ru.stas.cadence.samples.controller;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.stas.cadence.samples.propagator.TracingContextPropagator;
import ru.stas.cadence.samples.workflow.LongRunningWorkflow;
import ru.stas.cadence.samples.workflow.SampleConstants;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;



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

  @GetMapping("/start/longwf")
  private void startLongWF() {
    log.debug("start wf");
    WorkflowOptions options = new WorkflowOptions.Builder()
        .setExecutionStartToCloseTimeout(Duration.ofDays(365))
        .setTaskList(LongRunningWorkflow.TASK_LIST_MAIN)
        .setContextPropagators(Collections.singletonList(new TracingContextPropagator()))
        .build();
   // for(int i=0; i< 100; i++) {
      LongRunningWorkflow.LongWorkflow longWorkflow = workflowClient.newWorkflowStub(LongRunningWorkflow.LongWorkflow.class, options);
      WorkflowClient.start(longWorkflow::process, "NewWorld");
    //}
  }
}
