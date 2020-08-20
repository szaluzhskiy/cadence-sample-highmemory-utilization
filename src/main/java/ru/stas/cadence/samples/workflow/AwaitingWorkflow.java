/*
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package ru.stas.cadence.samples.workflow;

import com.uber.cadence.DomainAlreadyExistsError;
import com.uber.cadence.RegisterDomainRequest;
import com.uber.cadence.activity.Activity;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.*;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.stas.cadence.samples.propagator.TracingContextPropagator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ru.stas.cadence.samples.workflow.SampleConstants.DOMAIN;
import static ru.stas.cadence.samples.workflow.SampleConstants.TASK_LIST_MAIN;

/**
 * Demonstrates a child workflow. Requires a local instance of the Cadence server to be running.
 */
@Slf4j
@Component
public class AwaitingWorkflow implements ApplicationRunner {

  public final static String TASK_LIST_AWAITING = "AWAITING_WORKFLOW_TASK_LIST";

  public void run(ApplicationArguments args) {
    registerDomain();
    startFactory();
  }

  private void startFactory() {
    // Start a worker that hosts both parent and child workflow implementations.
    Worker.Factory factory =
        new Worker.Factory(
            "127.0.0.1",
            7933,
            DOMAIN,
            new Worker.FactoryOptions.Builder().setMaxWorkflowThreadCount(2).build());

    Worker workerParent =
        factory.newWorker(
            TASK_LIST_AWAITING, new WorkerOptions.Builder()
                .setMaxConcurrentWorkflowExecutionSize(1)
                .setMaxConcurrentActivityExecutionSize(1)
                .build()
        );

    workerParent.registerWorkflowImplementationTypes(AwaitingWorkflow.PaymentWorkflowImpl.class);
    workerParent.registerActivitiesImplementations(new AwaitingWorkflow.ParentActivitiesImpl());

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

  public interface PaymentWorkflow {
    /**
     * @return greeting string
     */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 3600)
    String payment(String paymentId);

    @SignalMethod
    void unblock();
  }

  public interface Activities {
    @ActivityMethod(scheduleToStartTimeoutSeconds = 3600, startToCloseTimeoutSeconds = 60)
    String record(int activityIdx);
  }

  static class ParentActivitiesImpl implements Activities {

    @Override
    public String record(int activityIdx) {
      return String.format("Finished activity: idx: [%d], activity id: [%s], task: [%s]", activityIdx,
          Activity.getTask().getActivityId(), new String(Activity.getTaskToken()));
    }
  }

  /**
   * GreetingWorkflow implementation that calls GreetingsActivities#printIt.
   */
  public static class PaymentWorkflowImpl implements PaymentWorkflow {

    private boolean lock = false;
    private String paymentId;

    @Override
    public String payment(String paymentId) {
      log.debug("START_WF PAYMENT {}", paymentId);
      this.paymentId = paymentId;
      Workflow.await(() -> lock);
      Promise<List<String>> parentPromises = runParentActivities();
      parentPromises.get();
      log.debug("FINISH_WF PAYMENT {}", paymentId);
      return "";
    }

    @Override
    public void unblock() {
      log.debug("UNLOCK_WF PAYMENT {}", paymentId);
      lock = true;
    }

    private Promise<List<String>> runParentActivities() {
      List<Promise<String>> parentActivities = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        Activities activity = Workflow.newActivityStub(Activities.class);
        parentActivities.add(Async.function(activity::record, i));
      }
      return Promise.allOf(parentActivities);
    }
  }
}