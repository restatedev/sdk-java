// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import java.lang.annotation.*;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Annotation to enable the Restate extension for JUnit 5. The annotation will bootstrap a Restate
 * environment using TestContainers, and will automatically register all services field of the class
 * annotated with {@link BindService}.
 *
 * <p>Example:
 *
 * <pre>
 * // Annotate the class as RestateTest to start a Restate environment
 * {@code @RestateTest}
 * class CounterTest {
 *
 *   // Annotate the service to bind
 *   {@code @BindService} private final Counter counter = new Counter();
 *
 *   // Inject the client to send requests
 *   {@code @Test}
 *   void testGreet({@code @RestateClient} Client ingressClient) {
 *     var client = CounterClient.fromClient(ingressClient, "my-counter");
 *
 *     long response = client.get();
 *     assertThat(response).isEqualTo(0L);
 *   }
 * }
 * </pre>
 *
 * <p>The runner will deploy the services locally, execute Restate as container using <a
 * href="https://java.testcontainers.org/">Testcontainers</a>, and register the services.
 *
 * <p>This extension is scoped per test class, meaning that the restate runner will be shared among
 * test methods. Because of the aforementioned issue, the extension sets the {@link TestInstance}
 * {@link TestInstance.Lifecycle#PER_CLASS} automatically.
 *
 * <p>Use the annotations {@link RestateClient}, {@link RestateURL} and {@link RestateAdminClient}
 * to interact with the deployed environment:
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
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(RestateExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public @interface RestateTest {

  /** Restate container image to use */
  String restateContainerImage() default "docker.io/restatedev/restate:latest";
}
