// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine.upcasting;

import static dev.restate.sdk.core.statemachine.ProtoUtils.END_MESSAGE;
import static dev.restate.sdk.core.statemachine.ProtoUtils.inputCmd;
import static dev.restate.sdk.core.statemachine.ProtoUtils.outputCmd;
import static dev.restate.sdk.core.statemachine.ProtoUtils.startMessage;

import dev.restate.common.Slice;
import dev.restate.common.function.ThrowingBiFunction;
import dev.restate.sdk.Context;
import dev.restate.sdk.HandlerRunner;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.WorkflowContext;
import dev.restate.sdk.core.MockRequestResponse;
import dev.restate.sdk.core.TestDefinitions;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.sdk.upcasting.Upcaster;
import dev.restate.sdk.upcasting.UpcasterFactory;
import dev.restate.serde.jackson.JacksonSerdeFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Integration-level tests verifying that configured {@link dev.restate.sdk.upcasting.Upcaster}
 * instances are applied to incoming inputs across different service types (Service, Virtual Object,
 * Workflow) using the state machine test harness.
 *
 * @author Milan Savic
 */
public class UpcasterTest {

  private static final String INPUT_ORIG = "orig-value";
  private static final String INPUT_UPCASTED = "upcasted-value";

  private static UpcasterFactory simpleReplacingUpcasterFactory() {
    return new UpcasterFactory() {
      @Override
      public Upcaster newUpcaster(
          String serviceName, ServiceType serviceType, Map<String, String> metadata) {
        return new Upcaster() {
          @Override
          public boolean canUpcast(Slice body, Map<String, String> headers) {
            // Upcast only if the input contains the marker "orig-value"
            return new String(body.toByteArray(), StandardCharsets.UTF_8).contains(INPUT_ORIG);
          }

          @Override
          public Slice upcast(Slice body, Map<String, String> headers) {
            String s = new String(body.toByteArray(), StandardCharsets.UTF_8);
            s = s.replace(INPUT_ORIG, INPUT_UPCASTED);
            return Slice.wrap(s);
          }
        };
      }
    };
  }

  private static <REQ, RES> ServiceDefinition buildService(
      String name,
      ServiceType type,
      HandlerType handlerType,
      ThrowingBiFunction<?, REQ, RES> runner) {
    @SuppressWarnings("unchecked")
    HandlerDefinition<REQ, RES> handler =
        HandlerDefinition.of(
            "run",
            handlerType,
            TestSerdes.STRING,
            TestSerdes.STRING,
            HandlerRunner.of((ThrowingBiFunction) runner, new JacksonSerdeFactory(), null));

    return ServiceDefinition.of(name, type, List.of(handler))
        .configure(cfg -> cfg.configureUpcasterFactory(simpleReplacingUpcasterFactory()));
  }

  @Test
  void serviceUpcasterIsAppliedToInput() {
    // Echo handler for Service
    ServiceDefinition svc =
        buildService(
            "UpcastService",
            ServiceType.SERVICE,
            HandlerType.SHARED,
            (ThrowingBiFunction<Context, String, String>) (ctx, in) -> in);

    TestDefinitions.ExpectingOutputMessages def =
        TestDefinitions.testInvocation(svc, null, "run")
            .withInput(startMessage(1), inputCmd(INPUT_ORIG))
            .expectingOutput(outputCmd(INPUT_UPCASTED), END_MESSAGE);
    MockRequestResponse.INSTANCE.executeTest(def);
  }

  @Test
  void virtualObjectUpcasterIsAppliedToInput() {
    // Echo handler for Virtual Object
    ServiceDefinition svc =
        buildService(
            "UpcastVirtualObject",
            ServiceType.VIRTUAL_OBJECT,
            HandlerType.EXCLUSIVE,
            (ThrowingBiFunction<ObjectContext, String, String>) (ctx, in) -> in);

    TestDefinitions.ExpectingOutputMessages def =
        TestDefinitions.testInvocation(svc, null, "run")
            .withInput(startMessage(1, "my-key"), inputCmd(INPUT_ORIG))
            .expectingOutput(outputCmd(INPUT_UPCASTED), END_MESSAGE);
    MockRequestResponse.INSTANCE.executeTest(def);
  }

  @Test
  void workflowUpcasterIsAppliedToInput() {
    // Echo handler for Workflow
    ServiceDefinition svc =
        buildService(
            "UpcastWorkflow",
            ServiceType.WORKFLOW,
            HandlerType.WORKFLOW,
            (ThrowingBiFunction<WorkflowContext, String, String>) (ctx, in) -> in);

    TestDefinitions.ExpectingOutputMessages def =
        TestDefinitions.testInvocation(svc, null, "run")
            .withInput(startMessage(1), inputCmd(INPUT_ORIG))
            .expectingOutput(outputCmd(INPUT_UPCASTED), END_MESSAGE);
    MockRequestResponse.INSTANCE.executeTest(def);
  }
}
