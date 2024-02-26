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
import dev.restate.sdk.common.ComponentAdapter;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

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
   * Add a Restate component to the endpoint. This will automatically discover the adapter based on
   * the class name. You can provide the adapter manually using {@link #with(Object,
   * ComponentAdapter)}
   */
  public RestateRunnerBuilder with(Object component) {
    endpointBuilder.with(component);
    return this;
  }

  /**
   * Add a Restate component to the endpoint, specifying the {@code executor} where to run the
   * component code. This will automatically discover the adapter based on the class name. You can
   * provide the adapter manually using {@link #with(Object, ComponentAdapter, Executor)}
   *
   * <p>You can run on virtual threads by using the executor {@code
   * Executors.newVirtualThreadPerTaskExecutor()}.
   */
  public RestateRunnerBuilder with(Object component, Executor executor) {
    endpointBuilder.with(component, executor);
    return this;
  }

  /** Add a Restate component to the endpoint, specifying an adapter. */
  public <T> RestateRunnerBuilder with(T component, ComponentAdapter<T> adapter) {
    endpointBuilder.with(component, adapter);
    return this;
  }

  /**
   * Add a Restate component to the endpoint, specifying the {@code executor} where to run the
   * component code.
   *
   * <p>You can run on virtual threads by using the executor {@code
   * Executors.newVirtualThreadPerTaskExecutor()}.
   */
  public <T> RestateRunnerBuilder with(
      T component, ComponentAdapter<T> adapter, Executor executor) {
    endpointBuilder.with(component, adapter, executor);
    return this;
  }

  /** Add a Restate bindable component to the endpoint. */
  public RestateRunnerBuilder with(BindableComponent component) {
    endpointBuilder.with(component);
    return this;
  }

  /**
   * Add a Restate bindable component to the endpoint, specifying the {@code executor} where to run
   * the component code.
   *
   * <p>You can run on virtual threads by using the executor {@code
   * Executors.newVirtualThreadPerTaskExecutor()}.
   */
  public RestateRunnerBuilder with(BindableComponent component, Executor executor) {
    endpointBuilder.with(component, executor);
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
