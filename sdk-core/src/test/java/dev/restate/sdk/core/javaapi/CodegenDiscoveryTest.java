// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi;

import static dev.restate.sdk.core.AssertUtils.assertThatDiscovery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.restate.sdk.core.generated.manifest.Handler;
import dev.restate.sdk.core.generated.manifest.Input;
import dev.restate.sdk.core.generated.manifest.Output;
import dev.restate.sdk.core.generated.manifest.Service;
import org.junit.jupiter.api.Test;

public class CodegenDiscoveryTest {

  @Test
  void checkCustomInputContentType() {
    assertThatDiscovery(new CodegenTest.RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInputWithCustomCt")
        .extracting(Handler::getInput, type(Input.class))
        .extracting(Input::getContentType)
        .isEqualTo("application/vnd.my.custom");
  }

  @Test
  void checkCustomInputAcceptContentType() {
    assertThatDiscovery(new CodegenTest.RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInputWithCustomAccept")
        .extracting(Handler::getInput, type(Input.class))
        .extracting(Input::getContentType)
        .isEqualTo("application/*");
  }

  @Test
  void checkCustomOutputContentType() {
    assertThatDiscovery(new CodegenTest.RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawOutputWithCustomCT")
        .extracting(Handler::getOutput, type(Output.class))
        .extracting(Output::getContentType)
        .isEqualTo("application/vnd.my.custom");
  }

  @Test
  void explicitNames() {
    assertThatDiscovery((GreeterWithExplicitName) (context, request) -> "")
        .extractingService("MyExplicitName")
        .extractingHandler("my_greeter");
    assertThat(GreeterWithExplicitNameHandlers.Metadata.SERVICE_NAME).isEqualTo("MyExplicitName");
  }

  @Test
  void workflowType() {
    assertThatDiscovery(new CodegenTest.MyWorkflow())
        .extractingService("MyWorkflow")
        .returns(Service.Ty.WORKFLOW, Service::getTy)
        .extractingHandler("run")
        .returns(Handler.Ty.WORKFLOW, Handler::getTy);
  }
}
