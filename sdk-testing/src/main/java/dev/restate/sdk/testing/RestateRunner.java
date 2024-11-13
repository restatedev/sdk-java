// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import dev.restate.sdk.client.Client;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.junit.jupiter.api.extension.*;

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
 *
 * @deprecated We now recommend using {@link RestateTest}.
 */
@Deprecated
public class RestateRunner implements BeforeAllCallback, ParameterResolver {

  static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(RestateRunner.class);
  static final String DEPLOYER_KEY = "Deployer";

  private final ManualRestateRunner deployer;

  RestateRunner(ManualRestateRunner deployer) {
    this.deployer = deployer;
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    deployer.start();
    context.getStore(NAMESPACE).put(DEPLOYER_KEY, deployer);
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return supportsParameter(parameterContext);
  }

  static boolean supportsParameter(ParameterContext parameterContext) {
    return (parameterContext.isAnnotated(RestateAdminClient.class)
            && dev.restate.admin.client.ApiClient.class.isAssignableFrom(
                parameterContext.getParameter().getType()))
        || (parameterContext.isAnnotated(RestateClient.class)
            && Client.class.isAssignableFrom(parameterContext.getParameter().getType()))
        || (parameterContext.isAnnotated(RestateURL.class)
            && (String.class.isAssignableFrom(parameterContext.getParameter().getType())
                || URL.class.isAssignableFrom(parameterContext.getParameter().getType())));
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    if (parameterContext.isAnnotated(RestateAdminClient.class)) {
      return getDeployer(extensionContext).getAdminClient();
    } else if (parameterContext.isAnnotated(RestateClient.class)) {
      return resolveClient(extensionContext);
    } else if (parameterContext.isAnnotated(RestateURL.class)) {
      URL url = getDeployer(extensionContext).getIngressUrl();
      if (parameterContext.getParameter().getType().equals(String.class)) {
        return url.toString();
      }
      if (parameterContext.getParameter().getType().equals(URI.class)) {
        try {
          return url.toURI();
        } catch (URISyntaxException e) {
          throw new RuntimeException(e);
        }
      }
      return url;
    }
    throw new ParameterResolutionException("The parameter is not supported");
  }

  private Client resolveClient(ExtensionContext extensionContext) {
    URL url = getDeployer(extensionContext).getIngressUrl();
    return Client.connect(url.toString());
  }

  private ManualRestateRunner getDeployer(ExtensionContext extensionContext) {
    return (ManualRestateRunner) extensionContext.getStore(NAMESPACE).get(DEPLOYER_KEY);
  }
}
