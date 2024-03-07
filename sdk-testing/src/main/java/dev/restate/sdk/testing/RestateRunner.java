// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Restate runner for JUnit 5. Example:
 *
 * <pre>{@code
 * {@literal @}RegisterExtension
 * private final static RestateRunner restateRunner = RestateRunnerBuilder.create()
 *         .withService(new MyService())
 *         .buildRunner();
 * }</pre>
 *
 * <p>The runner will deploy the services locally, execute Restate as container using
 * testcontainers, and register the services.
 *
 * <p>This extension is scoped per test class, meaning that the restate runner will be shared among
 * test methods.
 *
 * <p>Use the annotations {@link RestateIngressClient}, {@link RestateURL} and {@link
 * RestateAdminClient} to interact with the deployed runtime:
 *
 * <pre>{@code
 * {@literal @}Test
 * void testGreet({@literal @}RestateGrpcChannel ManagedChannel channel) {
 *     CounterGrpc.CounterBlockingStub client = CounterGrpc.newBlockingStub(channel);
 *     // Use client
 * }
 * }</pre>
 */
public class RestateRunner extends BaseRestateRunner implements BeforeAllCallback {
  private final ManualRestateRunner deployer;

  RestateRunner(ManualRestateRunner deployer) {
    this.deployer = deployer;
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    deployer.run();
    context.getStore(NAMESPACE).put(DEPLOYER_KEY, deployer);
  }
}
