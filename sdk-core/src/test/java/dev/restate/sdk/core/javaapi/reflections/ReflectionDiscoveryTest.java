// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi.reflections;

import static dev.restate.sdk.core.AssertUtils.assertThatDiscovery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.restate.sdk.core.generated.manifest.Handler;
import dev.restate.sdk.core.generated.manifest.Input;
import dev.restate.sdk.core.generated.manifest.Output;
import dev.restate.sdk.core.generated.manifest.Service;
import dev.restate.sdk.core.javaapi.GreeterWithExplicitName;
import dev.restate.sdk.core.javaapi.GreeterWithExplicitNameHandlers;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.serde.Serde;
import org.junit.jupiter.api.Test;

public class ReflectionDiscoveryTest {

  @Test
  void checkCustomInputContentType() {
    assertThatDiscovery(new RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInputWithCustomCt")
        .extracting(Handler::getInput, type(Input.class))
        .extracting(Input::getContentType)
        .isEqualTo("application/vnd.my.custom");
  }

  @Test
  void checkCustomInputAcceptContentType() {
    assertThatDiscovery(new RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInputWithCustomAccept")
        .extracting(Handler::getInput, type(Input.class))
        .extracting(Input::getContentType)
        .isEqualTo("application/*");
  }

  @Test
  void checkCustomOutputContentType() {
    assertThatDiscovery(new RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawOutputWithCustomCT")
        .extracting(Handler::getOutput, type(Output.class))
        .extracting(Output::getContentType)
        .isEqualTo("application/vnd.my.custom");
  }

  @Test
  void checkRawInputContentType() {
    assertThatDiscovery(new RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInput")
        .extracting(Handler::getInput, type(Input.class))
        .extracting(Input::getContentType)
        .isEqualTo(Serde.RAW.contentType());
  }

  @Test
  void checkRawOutputContentType() {
    assertThatDiscovery(new RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawOutput")
        .extracting(Handler::getOutput, type(Output.class))
        .extracting(Output::getContentType)
        .isEqualTo(Serde.RAW.contentType());
  }

  @Test
  void checkRawInfoFromInterface() {
    var handlerAssert =
        assertThatDiscovery(new RawServiceImpl())
            .extractingService("RawService")
            .extractingHandler("echo");

    handlerAssert
        .extracting(Handler::getInput, type(Input.class))
        .extracting(Input::getContentType)
        .isEqualTo(Serde.RAW.contentType());

    handlerAssert
        .extracting(Handler::getOutput, type(Output.class))
        .extracting(Output::getContentType)
        .isEqualTo(Serde.RAW.contentType());
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
    assertThatDiscovery(new MyWorkflow())
        .extractingService("MyWorkflow")
        .returns(Service.Ty.WORKFLOW, Service::getTy)
        .extractingHandler("run")
        .returns(Handler.Ty.WORKFLOW, Handler::getTy);
  }

  @Test
  void usingTransformer() {
    assertThatDiscovery(
            Endpoint.bind(
                new RawInputOutput(),
                sd ->
                    sd.documentation("My service documentation")
                        .configureHandler(
                            "rawInputWithCustomCt",
                            hd -> hd.documentation("My handler documentation"))))
        .extractingService("RawInputOutput")
        .returns("My service documentation", Service::getDocumentation)
        .extractingHandler("rawInputWithCustomCt")
        .returns("My handler documentation", Handler::getDocumentation);
  }
}
