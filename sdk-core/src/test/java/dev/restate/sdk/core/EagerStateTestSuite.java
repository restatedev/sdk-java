// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static dev.restate.sdk.core.statemachine.ProtoUtils.*;
import static dev.restate.sdk.core.TestDefinitions.*;
import static org.assertj.core.api.AssertionsForClassTypes.entry;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.ClearAllStateEntryMessage;
import dev.restate.generated.service.protocol.Protocol.Empty;
import java.util.Map;
import java.util.stream.Stream;

public abstract class EagerStateTestSuite implements TestSuite {

  protected abstract TestInvocationBuilder getEmpty();

  protected abstract TestInvocationBuilder get();

  protected abstract TestInvocationBuilder getAppendAndGet();

  protected abstract TestInvocationBuilder getClearAndGet();

  protected abstract TestInvocationBuilder getClearAllAndGet();

  protected abstract TestInvocationBuilder listKeys();

  protected abstract TestInvocationBuilder consecutiveGetWithEmpty();

  private static final Map.Entry<String, String> STATE_FRANCESCO = entry("STATE", "Francesco");
  private static final Map.Entry<String, String> ANOTHER_STATE_FRANCESCO =
      entry("ANOTHER_STATE", "Francesco");
  private static final MessageLite INPUT_TILL = inputMessage("Till");
  private static final MessageLite GET_STATE_FRANCESCO = getStateMessage("STATE", "Francesco");
  private static final MessageLite GET_STATE_FRANCESCO_TILL =
      getStateMessage("STATE", "FrancescoTill");
  private static final MessageLite SET_STATE_FRANCESCO_TILL =
      setStateMessage("STATE", "FrancescoTill");
  private static final MessageLite OUTPUT_FRANCESCO = outputMessage("Francesco");
  private static final MessageLite OUTPUT_FRANCESCO_TILL = outputMessage("FrancescoTill");

  @Override
  public Stream<TestDefinition> definitions() {
    return Stream.of(
        this.getEmpty()
            .withInput(startMessage(1).setPartialState(false), INPUT_TILL)
            .expectingOutput(getStateEmptyMessage("STATE"), outputMessage("true"), END_MESSAGE)
            .named("With complete state"),
        this.getEmpty()
            .withInput(startMessage(1).setPartialState(true), INPUT_TILL)
            .expectingOutput(getStateMessage("STATE"), suspensionMessage(1))
            .named("With partial state"),
        this.getEmpty()
            .withInput(
                startMessage(2).setPartialState(true), INPUT_TILL, getStateEmptyMessage("STATE"))
            .expectingOutput(outputMessage("true"), END_MESSAGE)
            .named("Resume with partial state"),
        this.get()
            .withInput(
                startMessage(1, "my-greeter", STATE_FRANCESCO).setPartialState(false), INPUT_TILL)
            .expectingOutput(GET_STATE_FRANCESCO, OUTPUT_FRANCESCO, END_MESSAGE)
            .named("With complete state"),
        this.get()
            .withInput(
                startMessage(1, "my-greeter", STATE_FRANCESCO).setPartialState(true), INPUT_TILL)
            .expectingOutput(GET_STATE_FRANCESCO, OUTPUT_FRANCESCO, END_MESSAGE)
            .named("With partial state"),
        this.get()
            .withInput(startMessage(1).setPartialState(true), INPUT_TILL)
            .expectingOutput(getStateMessage("STATE"), suspensionMessage(1))
            .named("With partial state without the state entry"),
        this.getAppendAndGet()
            .withInput(startMessage(1, "my-greeter", STATE_FRANCESCO), INPUT_TILL)
            .expectingOutput(
                GET_STATE_FRANCESCO,
                SET_STATE_FRANCESCO_TILL,
                GET_STATE_FRANCESCO_TILL,
                OUTPUT_FRANCESCO_TILL,
                END_MESSAGE)
            .named("With state in the state_map"),
        this.getAppendAndGet()
            .withInput(
                startMessage(1).setPartialState(true),
                INPUT_TILL,
                completionMessage(1, "Francesco"))
            .expectingOutput(
                getStateMessage("STATE"),
                SET_STATE_FRANCESCO_TILL,
                GET_STATE_FRANCESCO_TILL,
                OUTPUT_FRANCESCO_TILL,
                END_MESSAGE)
            .named("With partial state on the first get"),
        this.getClearAndGet()
            .withInput(startMessage(1, "my-greeter", STATE_FRANCESCO), INPUT_TILL)
            .expectingOutput(
                GET_STATE_FRANCESCO,
                clearStateMessage("STATE"),
                getStateEmptyMessage("STATE"),
                OUTPUT_FRANCESCO,
                END_MESSAGE)
            .named("With state in the state_map"),
        this.getClearAndGet()
            .withInput(
                startMessage(1).setPartialState(true),
                INPUT_TILL,
                completionMessage(1, "Francesco"))
            .expectingOutput(
                getStateMessage("STATE"),
                clearStateMessage("STATE"),
                getStateEmptyMessage("STATE"),
                OUTPUT_FRANCESCO,
                END_MESSAGE)
            .named("With partial state on the first get"),
        this.getClearAllAndGet()
            .withInput(
                startMessage(1, "my-greeter", STATE_FRANCESCO, ANOTHER_STATE_FRANCESCO), INPUT_TILL)
            .expectingOutput(
                GET_STATE_FRANCESCO,
                ClearAllStateEntryMessage.getDefaultInstance(),
                getStateEmptyMessage("STATE"),
                getStateEmptyMessage("ANOTHER_STATE"),
                OUTPUT_FRANCESCO,
                END_MESSAGE)
            .named("With state in the state_map"),
        this.getClearAllAndGet()
            .withInput(
                startMessage(1).setPartialState(true),
                INPUT_TILL,
                completionMessage(1, STATE_FRANCESCO.getValue()))
            .expectingOutput(
                getStateMessage("STATE"),
                ClearAllStateEntryMessage.getDefaultInstance(),
                getStateEmptyMessage("STATE"),
                getStateEmptyMessage("ANOTHER_STATE"),
                OUTPUT_FRANCESCO,
                END_MESSAGE)
            .named("With partial state on the first get"),
        this.listKeys()
            .withInput(
                startMessage(1, "my-greeter", STATE_FRANCESCO).setPartialState(true),
                INPUT_TILL,
                completionMessage(1, stateKeys("a", "b")))
            .expectingOutput(
                Protocol.GetStateKeysEntryMessage.getDefaultInstance(),
                outputMessage("a,b"),
                END_MESSAGE)
            .named("With partial state"),
        this.listKeys()
            .withInput(
                startMessage(1, "my-greeter", STATE_FRANCESCO).setPartialState(false), INPUT_TILL)
            .expectingOutput(
                Protocol.GetStateKeysEntryMessage.newBuilder()
                    .setValue(stateKeys(STATE_FRANCESCO.getKey())),
                outputMessage(STATE_FRANCESCO.getKey()),
                END_MESSAGE)
            .named("With complete state"),
        this.listKeys()
            .withInput(
                startMessage(2).setPartialState(true),
                INPUT_TILL,
                Protocol.GetStateKeysEntryMessage.newBuilder().setValue(stateKeys("3", "2", "1")))
            .expectingOutput(outputMessage("3,2,1"), END_MESSAGE)
            .named("With replayed list"),
        this.consecutiveGetWithEmpty()
            .withInput(startMessage(1).setPartialState(false), inputMessage())
            .expectingOutput(
                getStateMessage("key-0").setEmpty(Empty.getDefaultInstance()),
                getStateMessage("key-0").setEmpty(Empty.getDefaultInstance()),
                outputMessage(),
                END_MESSAGE),
        this.consecutiveGetWithEmpty()
            .withInput(
                startMessage(2).setPartialState(false),
                inputMessage(),
                getStateMessage("key-0").setEmpty(Empty.getDefaultInstance()))
            .expectingOutput(
                getStateMessage("key-0").setEmpty(Empty.getDefaultInstance()),
                outputMessage(),
                END_MESSAGE)
            .named("With replay of the first get"));
  }
}
