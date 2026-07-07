// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi.reflections;

import dev.restate.sdk.Restate;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Name;
import dev.restate.sdk.annotation.Workflow;

@Workflow
@Name("MyWorkflow")
public class MyWorkflow {

  @Workflow
  public void run(String myInput) {
    Restate.workflowHandle(MyWorkflow.class, Restate.key())
        .send(MyWorkflow::sharedHandler, myInput);
  }

  @Handler
  public String sharedHandler(String myInput) {
    return Restate.workflow(MyWorkflow.class, Restate.key()).sharedHandler(myInput);
  }
}
