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

package ru.stas.cadence.samples.signalling;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowQueryException;
import com.uber.cadence.testing.TestEnvironmentOptions;
import com.uber.cadence.testing.TestWorkflowEnvironment;
import com.uber.cadence.worker.Worker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Unit test for {@link HelloAwaitingActivity}. Doesn't use an external Cadence service. */
public class HelloAwaitingActivityTest {

  private TestWorkflowEnvironment testEnv;
  private Worker worker;
  private WorkflowClient workflowClient;

  @Before
  public void setUp() {
    Worker.FactoryOptions.Builder foBuilder = new Worker.FactoryOptions.Builder();
    foBuilder.setMaxWorkflowThreadCount(1);

    TestEnvironmentOptions.Builder teoBuilder = new TestEnvironmentOptions.Builder();
    teoBuilder.setFactoryOptions(foBuilder.build());

    testEnv = TestWorkflowEnvironment.newInstance(teoBuilder.build());
    worker = testEnv.newWorker(HelloAwaitingActivity.TASK_LIST);
    worker.registerWorkflowImplementationTypes(HelloAwaitingActivity.GreetingWorkflowImpl.class);

    workflowClient = testEnv.newWorkflowClient();
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  public void testActivityImpl() {
    worker.registerActivitiesImplementations(new HelloAwaitingActivity.GreetingActivitiesImpl());
    testEnv.start();

    List<HelloAwaitingActivity.GreetingWorkflow> wfs = new ArrayList();
    List<WorkflowExecution> wes = new ArrayList();
    // Get a workflow stub using the same task list the worker uses
    for (int i = 0; i < 2; i++) {
      HelloAwaitingActivity.GreetingWorkflow workflow =
          workflowClient.newWorkflowStub(HelloAwaitingActivity.GreetingWorkflow.class);
      // Execute a workflow waiting for it to complete.
      WorkflowExecution we = WorkflowClient.start(workflow::getGreeting, i, "World");
      System.out.println("OrderId - " + i + " wid - " + we.workflowId);
      wfs.add(workflow);
      wes.add(we);
    }
    /* try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }*/
    System.out.println("Number of workflows - " + wfs.size());
    wfs.parallelStream()
        .forEach(
            w -> {
              w.unlockSignal();
              try {
                System.out.println("SEND UNLOCK TO - " + w.getOrderId());
              } catch (WorkflowQueryException e) {
                System.out.println("FAIL IN QUERY");
              }
            });
    /*  wes.stream()
    .allMatch(
        we -> {
          WorkflowExecutionInfo wei =
              WorkflowExecutionUtils.describeWorkflowInstance(
                  testEnv.getWorkflowService().L, workflowClient.getDomain(), we);
          return WorkflowExecutionCloseStatus.findByValue(wei.closeStatus.getValue()) != null
              ? true
              : false;
        });*/
    try {
      Thread.sleep(20000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println("END!!!");
  }
}
