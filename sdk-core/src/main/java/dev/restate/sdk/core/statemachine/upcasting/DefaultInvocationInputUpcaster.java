// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine.upcasting;

import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toMap;

import com.google.protobuf.ByteString;
import dev.restate.common.Slice;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.statemachine.InvocationInput;
import dev.restate.sdk.core.statemachine.InvocationInputUpcaster;
import dev.restate.sdk.upcasting.Upcaster;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default implementation of {@link InvocationInputUpcaster} that applies an {@link Upcaster} to the
 * payloads contained in core protocol messages of an {@link InvocationInput}.
 *
 * <p>This component inspects the concrete protocol message carried by the invocation input and,
 * when a value payload is present, it invokes the configured {@link Upcaster} with metadata
 * describing the core message type and, where applicable, the state key. If the upcaster indicates
 * it can upcast, the payload is transformed and the message rebuilt with the new content. If any
 * exception occurs during upcasting, the public {@link #upcast(InvocationInput)} method logs a
 * warning and returns the original input unmodified.
 *
 * @author Milan Savic
 */
public class DefaultInvocationInputUpcaster implements InvocationInputUpcaster {

  private static final Logger LOG = LogManager.getLogger(DefaultInvocationInputUpcaster.class);

  private final Upcaster upcaster;

  public DefaultInvocationInputUpcaster(Upcaster upcaster) {
    this.upcaster = upcaster;
  }

  @Override
  public InvocationInput upcast(InvocationInput invocationInput) {
    try {
      return doUpcast(invocationInput);
    } catch (Exception e) {
      LOG.warn("An error occurred while upcasting.", e);
      return invocationInput;
    }
  }

  public InvocationInput doUpcast(InvocationInput invocationInput) {
    if (invocationInput == null) {
      return null;
    }
    if (invocationInput.message() instanceof Protocol.InputCommandMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.RunCompletionNotificationMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.NotificationTemplate msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.OutputCommandMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message()
        instanceof Protocol.GetLazyStateCompletionNotificationMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.SetStateCommandMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.GetEagerStateCommandMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message()
        instanceof Protocol.GetPromiseCompletionNotificationMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message()
        instanceof Protocol.PeekPromiseCompletionNotificationMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.CompletePromiseCommandMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message()
        instanceof Protocol.CallCompletionNotificationMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.SendSignalCommandMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message()
        instanceof Protocol.AttachInvocationCompletionNotificationMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message()
        instanceof Protocol.GetInvocationOutputCompletionNotificationMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.CompleteAwakeableCommandMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.SignalNotificationMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.StartMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    } else if (invocationInput.message() instanceof Protocol.ProposeRunCompletionMessage msg) {
      return InvocationInput.of(invocationInput.header(), upcast(msg));
    }
    return invocationInput;
  }

  private Protocol.InputCommandMessage upcast(Protocol.InputCommandMessage message) {
    Map<String, String> metadata =
        message.getHeadersList().stream()
            .collect(toMap(Protocol.Header::getKey, Protocol.Header::getKey));
    metadata.put(
        Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
        Protocol.InputCommandMessage.class.getSimpleName());
    return message.toBuilder().setValue(upcast(message.getValue(), metadata)).build();
  }

  private Protocol.RunCompletionNotificationMessage upcast(
      Protocol.RunCompletionNotificationMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.RunCompletionNotificationMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.NotificationTemplate upcast(Protocol.NotificationTemplate message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.NotificationTemplate.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.OutputCommandMessage upcast(Protocol.OutputCommandMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.OutputCommandMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.GetLazyStateCompletionNotificationMessage upcast(
      Protocol.GetLazyStateCompletionNotificationMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.GetLazyStateCompletionNotificationMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.SetStateCommandMessage upcast(Protocol.SetStateCommandMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.SetStateCommandMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.GetEagerStateCommandMessage upcast(
      Protocol.GetEagerStateCommandMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.GetEagerStateCommandMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.GetPromiseCompletionNotificationMessage upcast(
      Protocol.GetPromiseCompletionNotificationMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.GetPromiseCompletionNotificationMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.PeekPromiseCompletionNotificationMessage upcast(
      Protocol.PeekPromiseCompletionNotificationMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.PeekPromiseCompletionNotificationMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.CompletePromiseCommandMessage upcast(
      Protocol.CompletePromiseCommandMessage message) {
    if (message.hasCompletionValue()) {
      return message.toBuilder()
          .setCompletionValue(
              upcast(
                  message.getCompletionValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.CompletePromiseCommandMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.CallCompletionNotificationMessage upcast(
      Protocol.CallCompletionNotificationMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.CallCompletionNotificationMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.SendSignalCommandMessage upcast(Protocol.SendSignalCommandMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.SendSignalCommandMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.AttachInvocationCompletionNotificationMessage upcast(
      Protocol.AttachInvocationCompletionNotificationMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.AttachInvocationCompletionNotificationMessage.class
                          .getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.GetInvocationOutputCompletionNotificationMessage upcast(
      Protocol.GetInvocationOutputCompletionNotificationMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.GetInvocationOutputCompletionNotificationMessage.class
                          .getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.CompleteAwakeableCommandMessage upcast(
      Protocol.CompleteAwakeableCommandMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.CompleteAwakeableCommandMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.SignalNotificationMessage upcast(Protocol.SignalNotificationMessage message) {
    if (message.hasValue()) {
      return message.toBuilder()
          .setValue(
              upcast(
                  message.getValue(),
                  singletonMap(
                      Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                      Protocol.SignalNotificationMessage.class.getSimpleName())))
          .build();
    }
    return message;
  }

  private Protocol.StartMessage upcast(Protocol.StartMessage message) {
    var upcastedStateEntries = message.getStateMapList().stream().map(this::upcast).toList();
    return message.toBuilder().clearStateMap().addAllStateMap(upcastedStateEntries).build();
  }

  private Protocol.ProposeRunCompletionMessage upcast(
      Protocol.ProposeRunCompletionMessage message) {
    if (message.hasValue()) {
      Slice original = Slice.wrap(message.getValue().toByteArray());
      Slice upcastedValue =
          upcast(
              original,
              singletonMap(
                  Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
                  Protocol.ProposeRunCompletionMessage.class.getSimpleName()));
      return message.toBuilder().setValue(ByteString.copyFrom(upcastedValue.toByteArray())).build();
    }
    return message;
  }

  private Protocol.StartMessage.StateEntry upcast(Protocol.StartMessage.StateEntry message) {
    String key = new String(message.getKey().toByteArray());
    Map<String, String> metadata =
        Map.of(
            Upcaster.CORE_MESSAGE_NAME_METADATA_KEY,
            Protocol.StartMessage.class.getSimpleName(),
            Upcaster.STATE_KEY_METADATA_KEY,
            key);
    Slice value = Slice.wrap(message.getValue().toByteArray());

    Slice upcastedValue = upcast(value, metadata);

    return Protocol.StartMessage.StateEntry.newBuilder()
        .setKey(message.getKey())
        .setValue(ByteString.copyFrom(upcastedValue.toByteArray()))
        .build();
  }

  private Protocol.Value upcast(Protocol.Value message, Map<String, String> metadata) {
    Slice body = Slice.wrap(message.getContent().toByteArray());
    Slice upcastedSlice = upcast(body, metadata);
    return Protocol.Value.newBuilder()
        .setContent(ByteString.copyFrom(upcastedSlice.toByteArray()))
        .build();
  }

  private Slice upcast(Slice message, Map<String, String> metadata) {
    if (upcaster.canUpcast(message, metadata)) {
      return upcaster.upcast(message, metadata);
    }
    return message;
  }
}
