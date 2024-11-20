// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

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
    extensionContext
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            RUNNER, ignored -> initializeRestateRunner(extensionContext), RestateRunner.class)
        .beforeAll(extensionContext);
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return extensionContext
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            RUNNER, ignored -> initializeRestateRunner(extensionContext), RestateRunner.class)
        .supportsParameter(parameterContext, extensionContext);
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return extensionContext
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            RUNNER, ignored -> initializeRestateRunner(extensionContext), RestateRunner.class)
        .resolveParameter(parameterContext, extensionContext);
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
    var runnerBuilder = RestateRunnerBuilder.create();
    servicesToBind.forEach(runnerBuilder::bind);
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
    return runnerBuilder.buildRunner();
  }
}
