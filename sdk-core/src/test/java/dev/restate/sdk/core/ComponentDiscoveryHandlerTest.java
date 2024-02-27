// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.DescriptorProtos;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.sdk.core.testservices.CounterGrpc;
import dev.restate.sdk.core.testservices.GreeterGrpc;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentDiscoveryHandlerTest {

  @Test
  void handleWithMultipleServices() {
    ServiceDiscoveryHandler handler =
        new ServiceDiscoveryHandler(
            Discovery.ProtocolMode.REQUEST_RESPONSE,
            Map.of(
                GreeterGrpc.SERVICE_NAME, new GreeterGrpc.GreeterImplBase() {}.bindService(),
                CounterGrpc.SERVICE_NAME, new CounterGrpc.CounterImplBase() {}.bindService()));

    Discovery.ServiceDiscoveryResponse response =
        handler.handle(Discovery.ServiceDiscoveryRequest.getDefaultInstance());

    assertThat(response.getServicesList())
        .containsExactlyInAnyOrder(GreeterGrpc.SERVICE_NAME, CounterGrpc.SERVICE_NAME);
    assertThat(response.getFiles().getFileList())
        .map(DescriptorProtos.FileDescriptorProto::getName)
        .containsExactlyInAnyOrder(
            "dev/restate/ext.proto",
            "google/protobuf/descriptor.proto",
            "google/protobuf/empty.proto",
            "counter.proto",
            "common.proto",
            "greeter.proto");
    assertThat(response.getProtocolMode()).isEqualTo(Discovery.ProtocolMode.REQUEST_RESPONSE);
  }
}
