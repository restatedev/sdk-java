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
 * <pre>
 * {@code @RegisterExtension}
 * private final static RestateRunner restateRunner = RestateRunnerBuilder.create()
 *         .withService(new MyService())
 *         .buildRunner();
 * </pre>
 *
 * <p>The runner will deploy the services locally, execute Restate as container using <a
 * href="https://java.testcontainers.org/">Testcontainers</a>, and register the services.
 *
 * <p>This extension is scoped per test class, meaning that the restate runner will be shared among
 * test methods.
 *
 * <p>Use the annotations {@link RestateClient}, {@link RestateURL} and {@link RestateAdminClient}
 * to interact with the deployed server:
 *
 * <pre>
 * {@code @Test}
 * void initialCountIsZero({@code @RestateClient} Client client) {
 *     var client = CounterClient.fromClient(ingressClient, "my-counter");
 *
 *     // Use client as usual
 *     long response = client.get();
 *     assertThat(response).isEqualTo(0L);
 * }</pre>
 */
public class RestateRunner extends BaseRestateRunner implements BeforeAllCallback {
  private final ManualRestateRunner deployer;

  RestateRunner(ManualRestateRunner deployer) {
    this.deployer = deployer;
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    deployer.start();
    context.getStore(NAMESPACE).put(DEPLOYER_KEY, deployer);
  }
}
