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
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Promise;
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
public class ParentWorkflow implements ApplicationRunner {

  public void run(ApplicationArguments args) {
    registerDomain();
    startFactory();
   // startClient();
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
            new Worker.FactoryOptions.Builder().setMetricScope(scope).setContextPropagators(Arrays.asList(new TracingContextPropagator())).build());

    Worker workerParent =
        factory.newWorker(
            TASK_LIST_MAIN, new WorkerOptions.Builder().setMetricsScope(scope).build()
        );

    workerParent.registerWorkflowImplementationTypes(ParentWorkflow.GreetingWorkflowImpl.class);
    workerParent.registerWorkflowImplementationTypes(CancellableWorkflow.GreetingWorkflowImpl.class);
    workerParent.registerActivitiesImplementations(new ParentWorkflow.ParentActivitiesImpl());
    workerParent.registerActivitiesImplementations(new CancellableWorkflow.GreetingActivitiesImpl());

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
  public interface GreetingWorkflow {
    /**
     * @return greeting string
     */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000)
    String getGreeting(String name);
  }

  public interface ParentActivities {
    @ActivityMethod(scheduleToStartTimeoutSeconds = 60000, startToCloseTimeoutSeconds = 60)
    String composeParentGreeting(int activityIdx);
  }

  static class ParentActivitiesImpl implements ParentActivities {
    @Override
    public String composeParentGreeting(int activityIdx) {
      log.debug("ParentActivities::composeParentGreeting");
      return String.format("Finished parent activity: idx: [%d], activity id: [%s], task: [%s]", activityIdx,
          Activity.getTask().getActivityId(), new String(Activity.getTaskToken()));
    }
  }

  /**
   * GreetingWorkflow implementation that calls GreetingsActivities#printIt.
   */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    @Override
    public String getGreeting(String name) {
      log.debug("START WF");
      // Do something else here.
      Promise<List<String>> parentPromises = runParentActivities();
      parentPromises.get();
      log.debug("Got result in parent workflow");
      return ""; // blocks waiting for the child to complete.
    }

    private Promise<List<String>> runParentActivities() {
      RetryOptions ro =
          new RetryOptions.Builder()
              .setInitialInterval(java.time.Duration.ofSeconds(30))
              .setMaximumInterval(java.time.Duration.ofSeconds(30))
              .setMaximumAttempts(10)
              .build();
      ActivityOptions ao =
          new ActivityOptions.Builder()
              .setTaskList(TASK_LIST_MAIN)
              .setContextPropagators(Arrays.asList(new TracingContextPropagator()))
              .setRetryOptions(ro)
              .build();

      List<Promise<String>> parentActivities = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        ParentActivities activity = Workflow.newActivityStub(ParentActivities.class, ao);
        parentActivities.add(Async.function(activity::composeParentGreeting, i));
      }

      return Promise.allOf(parentActivities);
    }
  }
}