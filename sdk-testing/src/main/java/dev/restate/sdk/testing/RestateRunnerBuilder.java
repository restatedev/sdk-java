// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import dev.restate.sdk.common.BindableComponent;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import java.util.HashMap;
import java.util.Map;

/** Builder for {@link RestateRunner}. See {@link RestateRunner} for more details. */
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
   * Add a Restate component to the endpoint. This will automatically discover the generated factory
   * based on the class name.
   *
   * <p>You can also manually instantiate the {@link BindableComponent} using {@link
   * #with(BindableComponent)}.
   */
  public RestateRunnerBuilder with(Object component) {
    endpointBuilder.with(component);
    return this;
  }

  /**
   * Add a Restate bindable component to the endpoint.
   *
   * <p>To override the options, use {@link #with(BindableComponent, Object)}.
   */
  public RestateRunnerBuilder with(BindableComponent<?> component) {
    endpointBuilder.with(component);
    return this;
  }

  /** Add a Restate bindable component to the endpoint, overriding the options. */
  public <O> RestateRunnerBuilder with(BindableComponent<O> component, O options) {
    endpointBuilder.with(component, options);
    return this;
  }

  public ManualRestateRunner buildManualRunner() {
    return new ManualRestateRunner(
        this.endpointBuilder.build(),
        this.restateContainerImage,
        this.additionalEnv,
        this.configFile);
  }

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
