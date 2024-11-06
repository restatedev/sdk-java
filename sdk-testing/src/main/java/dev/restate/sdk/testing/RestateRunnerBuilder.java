// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import dev.restate.sdk.common.syscalls.ServiceDefinition;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder for {@link RestateRunner}.
 *
 * @see RestateRunner
 */
public class RestateRunnerBuilder {

  private static final String DEFAULT_RESTATE_CONTAINER = "docker.io/restatedev/restate";
  private final RestateHttpEndpointBuilder endpointBuilder;
  private String restateContainerImage = DEFAULT_RESTATE_CONTAINER;
  private final Map<String, String> additionalEnv = new HashMap<>();
  private String configFile;

  RestateRunnerBuilder(RestateHttpEndpointBuilder endpointBuilder) {
    this.endpointBuilder = endpointBuilder;
  }

  /** Override the container image to use for the Restate runtime. */
  public RestateRunnerBuilder withRestateContainerImage(String restateContainerImage) {
    this.restateContainerImage = restateContainerImage;
    return this;
  }

  /** Add additional environment variables to the Restate container. */
  public RestateRunnerBuilder withAdditionalEnv(String key, String value) {
    this.additionalEnv.put(key, value);
    return this;
  }

  /** Mount a config file in the Restate container. */
  public RestateRunnerBuilder withConfigFile(String configFile) {
    this.configFile = configFile;
    return this;
  }

  /**
   * Add a Restate service to the endpoint. This will automatically discover the generated factory
   * based on the class name.
   *
   * <p>You can also manually instantiate the {@link ServiceDefinition} using {@link
   * #bind(ServiceDefinition)}.
   */
  public RestateRunnerBuilder bind(Object service) {
    this.endpointBuilder.bind(service);
    return this;
  }

  /**
   * Add a Restate service to the endpoint.
   *
   * <p>To set the options, use {@link #bind(ServiceDefinition, Object)}.
   */
  public RestateRunnerBuilder bind(ServiceDefinition<?> serviceDefinition) {
    //noinspection unchecked
    this.endpointBuilder.bind((ServiceDefinition<Object>) serviceDefinition, null);
    return this;
  }

  /** Add a Restate service to the endpoint, setting the options. */
  public <O> RestateRunnerBuilder bind(ServiceDefinition<O> serviceDefinition, O options) {
    this.endpointBuilder.bind(serviceDefinition, options);
    return this;
  }

  /**
   * @return a {@link ManualRestateRunner} to start and stop the test infra manually.
   */
  public ManualRestateRunner buildManualRunner() {
    return new ManualRestateRunner(
        this.endpointBuilder.build(),
        this.restateContainerImage,
        this.additionalEnv,
        this.configFile);
  }

  /**
   * @return a {@link RestateRunner} to be used as JUnit 5 Extension.
   */
  public RestateRunner buildRunner() {
    return new RestateRunner(this.buildManualRunner());
  }

  public static RestateRunnerBuilder create() {
    return new RestateRunnerBuilder(RestateHttpEndpointBuilder.builder());
  }

  /** Create from {@link RestateHttpEndpointBuilder}. */
  public static RestateRunnerBuilder of(RestateHttpEndpointBuilder endpointBuilder) {
    return new RestateRunnerBuilder(endpointBuilder);
  }
}
