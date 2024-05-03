// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static dev.restate.sdk.core.AssertUtils.assertThatDiscovery;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.restate.sdk.core.MockMultiThreaded;
import dev.restate.sdk.core.MockSingleThread;
import dev.restate.sdk.core.TestDefinitions.TestExecutor;
import dev.restate.sdk.core.TestDefinitions.TestSuite;
import dev.restate.sdk.core.TestRunner;
import dev.restate.sdk.core.manifest.Input;
import dev.restate.sdk.core.manifest.Output;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class JavaCodegenTests extends TestRunner {

  @Override
  protected Stream<TestExecutor> executors() {
    return Stream.of(MockSingleThread.INSTANCE, MockMultiThreaded.INSTANCE);
  }

  @Override
  public Stream<TestSuite> definitions() {
    return Stream.of(new CodegenTest());
  }

  @Test
  void checkCustomInputContentType() {
    assertThatDiscovery(new CodegenTest.RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInputWithCustomCt")
        .extracting(dev.restate.sdk.core.manifest.Handler::getInput, type(Input.class))
        .extracting(Input::getContentType)
        .isEqualTo("application/vnd.my.custom");
  }

  @Test
  void checkCustomInputAcceptContentType() {
    assertThatDiscovery(new CodegenTest.RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInputWithCustomAccept")
        .extracting(dev.restate.sdk.core.manifest.Handler::getInput, type(Input.class))
        .extracting(Input::getContentType)
        .isEqualTo("application/*");
  }

  @Test
  void checkCustomOutputContentType() {
    assertThatDiscovery(new CodegenTest.RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawOutputWithCustomCT")
        .extracting(dev.restate.sdk.core.manifest.Handler::getOutput, type(Output.class))
        .extracting(Output::getContentType)
        .isEqualTo("application/vnd.my.custom");
  }
}
