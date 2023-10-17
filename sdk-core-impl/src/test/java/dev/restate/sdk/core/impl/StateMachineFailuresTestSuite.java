package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.AssertUtils.containsOnly;
import static dev.restate.sdk.core.impl.AssertUtils.errorMessageStartingWith;
import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;

import dev.restate.generated.sdk.java.Java;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import io.grpc.BindableService;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

public abstract class StateMachineFailuresTestSuite extends CoreTestRunner {

  protected abstract BindableService getState();

  protected abstract BindableService sideEffectFailure(TypeTag<Integer> typeTag);

  private static final TypeTag<Integer> FAILING_SERIALIZATION_INTEGER_TYPE_TAG =
      TypeTag.using(
          i -> {
            throw new IllegalStateException("Cannot serialize integer");
          },
          b -> Integer.parseInt(new String(b, StandardCharsets.UTF_8)));

  private static final TypeTag<Integer> FAILING_DESERIALIZATION_INTEGER_TYPE_TAG =
      TypeTag.using(
          i -> Integer.toString(i).getBytes(StandardCharsets.UTF_8),
          b -> {
            throw new IllegalStateException("Cannot deserialize integer");
          });

  @Override
  protected Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::getState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("Something"))
            .usingAllThreadingModels()
            .assertingOutput(
                containsOnly(
                    AssertUtils.protocolExceptionErrorMessage(
                        ProtocolException.JOURNAL_MISMATCH_CODE))),
        testInvocation(this::getState, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                getStateMessage("STATE", "This is not an integer"))
            .usingAllThreadingModels()
            .assertingOutput(
                containsOnly(
                    errorMessageStartingWith(NumberFormatException.class.getCanonicalName()))),
        testInvocation(
                () -> this.sideEffectFailure(FAILING_SERIALIZATION_INTEGER_TYPE_TAG),
                GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1), inputMessage(GreetingRequest.newBuilder().setName("Till")))
            .usingAllThreadingModels()
            .assertingOutput(
                containsOnly(
                    errorMessageStartingWith(IllegalStateException.class.getCanonicalName()))),
        testInvocation(
                () -> this.sideEffectFailure(FAILING_DESERIALIZATION_INTEGER_TYPE_TAG),
                GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2),
                inputMessage(GreetingRequest.newBuilder().setName("Till")),
                Java.SideEffectEntryMessage.newBuilder())
            .usingAllThreadingModels()
            .assertingOutput(
                containsOnly(
                    errorMessageStartingWith(IllegalStateException.class.getCanonicalName()))));
  }
}
