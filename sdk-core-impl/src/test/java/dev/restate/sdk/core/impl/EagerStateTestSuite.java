package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.CoreTestRunner.TestCaseBuilder.testInvocation;
import static dev.restate.sdk.core.impl.ProtoUtils.*;
import static org.assertj.core.api.AssertionsForClassTypes.entry;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.impl.testservices.GreeterGrpc;
import io.grpc.BindableService;
import java.util.Map;
import java.util.stream.Stream;

public abstract class EagerStateTestSuite extends CoreTestRunner {

  protected abstract BindableService getEmpty();

  protected abstract BindableService get();

  protected abstract BindableService getAppendAndGet();

  protected abstract BindableService getClearAndGet();

  private static final Map.Entry<String, String> STATE_FRANCESCO = entry("STATE", "Francesco");
  private static final MessageLite INPUT_TILL = inputMessage(greetingRequest("Till"));
  private static final MessageLite GET_STATE_FRANCESCO = getStateMessage("STATE", "Francesco");
  private static final MessageLite GET_STATE_FRANCESCO_TILL =
      getStateMessage("STATE", "FrancescoTill");
  private static final MessageLite SET_STATE_FRANCESCO_TILL =
      setStateMessage("STATE", "FrancescoTill");
  private static final MessageLite OUTPUT_FRANCESCO = outputMessage(greetingResponse("Francesco"));
  private static final MessageLite OUTPUT_FRANCESCO_TILL =
      outputMessage(greetingResponse("FrancescoTill"));

  @Override
  protected Stream<TestDefinition> definitions() {
    return Stream.of(
        testInvocation(this::getEmpty, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1).setPartialState(false), INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(getStateEmptyMessage("STATE"), outputMessage(greetingResponse("true")))
            .named("With complete state"),
        testInvocation(this::getEmpty, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1).setPartialState(true), INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(getStateMessage("STATE"), suspensionMessage(1))
            .named("With partial state"),
        testInvocation(this::getEmpty, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(2).setPartialState(true), INPUT_TILL, getStateEmptyMessage("STATE"))
            .usingAllThreadingModels()
            .expectingOutput(outputMessage(greetingResponse("true")))
            .named("Resume with partial state"),
        testInvocation(this::get, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1, STATE_FRANCESCO).setPartialState(false), INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(GET_STATE_FRANCESCO, OUTPUT_FRANCESCO)
            .named("With complete state"),
        testInvocation(this::get, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1, STATE_FRANCESCO).setPartialState(true), INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(GET_STATE_FRANCESCO, OUTPUT_FRANCESCO)
            .named("With partial state"),
        testInvocation(this::get, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1).setPartialState(true), INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(getStateMessage("STATE"), suspensionMessage(1))
            .named("With partial state without the state entry"),
        testInvocation(this::getAppendAndGet, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1, STATE_FRANCESCO), INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(
                GET_STATE_FRANCESCO,
                SET_STATE_FRANCESCO_TILL,
                GET_STATE_FRANCESCO_TILL,
                OUTPUT_FRANCESCO_TILL)
            .named("With state in the state_map"),
        testInvocation(this::getAppendAndGet, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1).setPartialState(true),
                INPUT_TILL,
                completionMessage(1, "Francesco"))
            .usingAllThreadingModels()
            .expectingOutput(
                getStateMessage("STATE"),
                SET_STATE_FRANCESCO_TILL,
                GET_STATE_FRANCESCO_TILL,
                OUTPUT_FRANCESCO_TILL)
            .named("With partial state on the first get"),
        testInvocation(this::getClearAndGet, GreeterGrpc.getGreetMethod())
            .withInput(startMessage(1, STATE_FRANCESCO), INPUT_TILL)
            .usingAllThreadingModels()
            .expectingOutput(
                GET_STATE_FRANCESCO,
                clearStateMessage("STATE"),
                getStateEmptyMessage("STATE"),
                OUTPUT_FRANCESCO)
            .named("With state in the state_map"),
        testInvocation(this::getClearAndGet, GreeterGrpc.getGreetMethod())
            .withInput(
                startMessage(1).setPartialState(true),
                INPUT_TILL,
                completionMessage(1, "Francesco"))
            .usingAllThreadingModels()
            .expectingOutput(
                getStateMessage("STATE"),
                clearStateMessage("STATE"),
                getStateEmptyMessage("STATE"),
                OUTPUT_FRANCESCO)
            .named("With partial state on the first get"));
  }
}
