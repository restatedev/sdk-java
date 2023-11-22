// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import dev.restate.sdk.core.BindableBlockingService;
import dev.restate.sdk.core.BindableNonBlockingService;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import io.grpc.ServerInterceptor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** Builder for {@link RestateRunner}. See {@link RestateRunner} for more details. */
public class RestateRunnerBuilder {

  private static final String DEFAULT_RUNTIME_CONTAINER = "ghcr.io/restatedev/restate-dist";

  private final RestateHttpEndpointBuilder endpointBuilder;
  private String runtimeContainerImage = DEFAULT_RUNTIME_CONTAINER;
  private final Map<String, String> additionalEnv = new HashMap<>();
  private String configFile;

  RestateRunnerBuilder(RestateHttpEndpointBuilder endpointBuilder) {
    this.endpointBuilder = endpointBuilder;
  }

  /** Override the container image to use for the Restate runtime. */
  public RestateRunnerBuilder withRuntimeContainerImage(String runtimeContainerImage) {
    this.runtimeContainerImage = runtimeContainerImage;
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
   * Register a service. See {@link RestateHttpEndpointBuilder#withService(BindableBlockingService,
   * ServerInterceptor...)}.
   */
  public RestateRunnerBuilder withService(
      BindableBlockingService service, ServerInterceptor... interceptors) {
    this.endpointBuilder.withService(service, interceptors);
    return this;
  }

  /**
   * Register a service. See {@link RestateHttpEndpointBuilder#withService(BindableBlockingService,
   * Executor, ServerInterceptor...)}.
   */
  public RestateRunnerBuilder withService(
      BindableBlockingService service, Executor executor, ServerInterceptor... interceptors) {
    this.endpointBuilder.withService(service, executor, interceptors);
    return this;
  }

  /**
   * Register a service. See {@link
   * RestateHttpEndpointBuilder#withService(BindableNonBlockingService, ServerInterceptor...)}.
   */
  public RestateRunnerBuilder withService(
      BindableNonBlockingService service, ServerInterceptor... interceptors) {
    this.endpointBuilder.withService(service, interceptors);
    return this;
  }

  public ManualRestateRunner buildManualRunner() {
    return new ManualRestateRunner(
        this.endpointBuilder.build(),
        this.runtimeContainerImage,
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
