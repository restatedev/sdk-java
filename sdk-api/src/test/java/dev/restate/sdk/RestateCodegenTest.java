// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.core.RestateCodegenTestSuite;
import dev.restate.sdk.core.testservices.*;
import io.grpc.BindableService;
import java.time.Duration;

public class RestateCodegenTest extends RestateCodegenTestSuite {

  private static class GreeterWithRestateClientAndServerCodegen
      extends GreeterRestate.GreeterRestateImplBase {

    @Override
    public GreetingResponse greet(RestateContext context, GreetingRequest request) {
      GreeterRestate.GreeterRestateClient client = GreeterRestate.newClient(context);
      client.delayed(Duration.ofSeconds(1)).greet(request);
      client.oneWay().greet(request);
      return client.greet(request).await();
    }
  }

  @Override
  protected BindableService greeterWithRestateClientAndServerCodegen() {
    return new GreeterWithRestateClientAndServerCodegen();
  }

  private static class Codegen extends CodegenRestate.CodegenRestateImplBase {

    @Override
    public MyMessage emptyInput(RestateContext context) {
      CodegenRestate.CodegenRestateClient client = CodegenRestate.newClient(context);
      return client.emptyInput().await();
    }

    @Override
    public void emptyOutput(RestateContext context, MyMessage request) {
      CodegenRestate.CodegenRestateClient client = CodegenRestate.newClient(context);
      client.emptyOutput(request).await();
    }

    @Override
    public void emptyInputOutput(RestateContext context) {
      CodegenRestate.CodegenRestateClient client = CodegenRestate.newClient(context);
      client.emptyInputOutput().await();
    }

    @Override
    public MyMessage oneWay(RestateContext context, MyMessage request) {
      CodegenRestate.CodegenRestateClient client = CodegenRestate.newClient(context);
      return client._oneWay(request).await();
    }

    @Override
    public MyMessage delayed(RestateContext context, MyMessage request) {
      CodegenRestate.CodegenRestateClient client = CodegenRestate.newClient(context);
      return client._delayed(request).await();
    }
  }

  @Override
  protected BindableService codegen() {
    return new Codegen();
  }
}
