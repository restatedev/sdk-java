// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.dynrpc.CodegenUtils;
import dev.restate.sdk.dynrpc.generated.KeyedRpcRequest;
import dev.restate.sdk.dynrpc.generated.RpcRequest;
import dev.restate.sdk.dynrpc.generated.RpcResponse;

public class JsonProtoUtils {

  private JsonProtoUtils() {}

  public static Protocol.PollInputStreamEntryMessage inputMessage(String value) {
    return Protocol.PollInputStreamEntryMessage.newBuilder()
        .setValue(
            RpcRequest.newBuilder()
                .setRequest(CodegenUtils.tToValue(CoreSerdes.JSON_STRING, value))
                .build()
                .toByteString())
        .build();
  }

  public static Protocol.PollInputStreamEntryMessage keyedInputMessage(String key, String value) {
    return Protocol.PollInputStreamEntryMessage.newBuilder()
        .setValue(
            KeyedRpcRequest.newBuilder()
                .setKey(key)
                .setRequest(CodegenUtils.tToValue(CoreSerdes.JSON_STRING, value))
                .build()
                .toByteString())
        .build();
  }

  public static Protocol.OutputStreamEntryMessage outputMessage(String value) {
    return Protocol.OutputStreamEntryMessage.newBuilder()
        .setValue(
            RpcResponse.newBuilder()
                .setResponse(CodegenUtils.tToValue(CoreSerdes.JSON_STRING, value))
                .build()
                .toByteString())
        .build();
  }
}
