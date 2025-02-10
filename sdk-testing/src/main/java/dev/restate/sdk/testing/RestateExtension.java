// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import dev.restate.client.Client;
import dev.restate.sdk.endpoint.Endpoint;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * @see RestateTest
 */
public class RestateExtension implements BeforeAllCallback, ParameterResolver {

  static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(RestateExtension.class);
  static final String RUNNER = "Runner";

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    getOrCreateRunner(extensionContext).start();
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
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
    RestateRunner runner = getOrCreateRunner(extensionContext);
    if (parameterContext.isAnnotated(RestateAdminClient.class)) {
      return runner.getAdminClient();
    } else if (parameterContext.isAnnotated(RestateClient.class)) {
      URL url = runner.getIngressUrl();
      return Client.connect(url.toString());
    } else if (parameterContext.isAnnotated(RestateURL.class)) {
      URL url = runner.getIngressUrl();
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

  private RestateRunner getOrCreateRunner(ExtensionContext extensionContext) {
    return extensionContext
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            RUNNER, ignored -> initializeRestateRunner(extensionContext), RestateRunner.class);
  }

  private RestateRunner initializeRestateRunner(ExtensionContext extensionContext) {
    // Discover services
    List<Object> servicesToBind =
        AnnotationSupport.findAnnotatedFieldValues(
            extensionContext.getRequiredTestInstance(), BindService.class);
    if (servicesToBind.isEmpty()) {
      throw new IllegalStateException(
          "The class "
              + extensionContext.getRequiredTestClass().getName()
              + " is annotated with @RestateTest, but there are no fields annotated with @BindService");
    }

    RestateTest testAnnotation =
        AnnotationSupport.findAnnotation(extensionContext.getRequiredTestClass(), RestateTest.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expecting @RestateTest annotation on the test class"));

    // Build runner discovering services to bind
    Endpoint.Builder endpointBuilder = Endpoint.builder();
    servicesToBind.forEach(endpointBuilder::bind);

    var runnerBuilder = RestateRunner.from(endpointBuilder.build());
    runnerBuilder.withRestateContainerImage(testAnnotation.containerImage());
    if (testAnnotation.environment() != null) {
      for (String env : testAnnotation.environment()) {
        String[] splitEnv = env.split(Pattern.quote("="), 2);
        if (splitEnv.length != 2) {
          throw new IllegalStateException(
              "Environment variables should be passed in the form of NAME=VALUE. Found an invalid env: '"
                  + env
                  + "'");
        }
        runnerBuilder.withAdditionalEnv(splitEnv[0], splitEnv[1]);
      }
    }
    return runnerBuilder.build();
  }
}
