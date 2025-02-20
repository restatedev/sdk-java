// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import dev.restate.admin.api.DeploymentApi;
import dev.restate.admin.client.ApiClient;
import dev.restate.admin.client.ApiException;
import dev.restate.admin.model.RegisterDeploymentRequest;
import dev.restate.admin.model.RegisterDeploymentRequestAnyOf;
import dev.restate.admin.model.RegisterDeploymentResponse;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import io.vertx.core.http.HttpServer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * Manual runner for the Restate test infra, starting the Restate server container together with the
 * provided services and automatically registering them. To start the infra use {@link #start()} and
 * to stop it use {@link #stop()}.
 *
 * <p>If you use JUnit 5, we suggest using {@link RestateTest} instead.
 */
public class RestateRunner implements AutoCloseable, ExtensionContext.Store.CloseableResource {

  private static final Logger LOG = LogManager.getLogger(RestateRunner.class);

  private static final String RESTATE_RUNTIME = "runtime";
  public static final int RESTATE_INGRESS_ENDPOINT_PORT = 8080;
  public static final int RESTATE_ADMIN_ENDPOINT_PORT = 9070;

  private final HttpServer server;
  private final GenericContainer<?> runtimeContainer;

  RestateRunner(
      Endpoint endpoint,
      String runtimeContainerImage,
      Map<String, String> additionalEnv,
      String configFile) {
    this.server = RestateHttpServer.fromEndpoint(endpoint);
    this.runtimeContainer = new GenericContainer<>(DockerImageName.parse(runtimeContainerImage));

    // Configure runtimeContainer
    this.runtimeContainer
        // We expose these ports only to enable port checks
        .withExposedPorts(RESTATE_INGRESS_ENDPOINT_PORT, RESTATE_ADMIN_ENDPOINT_PORT)
        // Let's have a high logging level by default to avoid spamming too much, it can be
        // overriden by the user
        .withEnv("RUST_LOG", "warn")
        .withEnv(additionalEnv)
        // These envs should not be overriden by additionalEnv
        .withEnv("RESTATE_META__REST_ADDRESS", "0.0.0.0:" + RESTATE_ADMIN_ENDPOINT_PORT)
        .withEnv(
            "RESTATE_WORKER__INGRESS__BIND_ADDRESS", "0.0.0.0:" + RESTATE_INGRESS_ENDPOINT_PORT)
        .withNetworkAliases(RESTATE_RUNTIME)
        // Configure wait strategy on health paths
        .waitingFor(
            new WaitAllStrategy()
                .withStrategy(Wait.forHttp("/health").forPort(RESTATE_ADMIN_ENDPOINT_PORT))
                .withStrategy(
                    Wait.forHttp("/restate/health").forPort(RESTATE_INGRESS_ENDPOINT_PORT)))
        .withLogConsumer(
            outputFrame -> {
              switch (outputFrame.getType()) {
                case STDOUT, STDERR ->
                    LOG.debug("[restate] {}", outputFrame.getUtf8StringWithoutLineEnding());
                case END -> LOG.debug("[restate] END");
              }
            });

    if (configFile != null) {
      this.runtimeContainer.withCopyToContainer(Transferable.of(configFile), "/config.yaml");
      this.runtimeContainer.withEnv("RESTATE_CONFIG", "/config.yaml");
    }
  }

  /** Create from {@link Endpoint}. */
  public static Builder from(Endpoint endpoint) {
    return new Builder(endpoint);
  }

  /**
   * Builder for {@link RestateRunner}.
   *
   * @see RestateRunner
   */
  public static class Builder {

    private static final String DEFAULT_RESTATE_CONTAINER = "docker.io/restatedev/restate:latest";
    private final Endpoint endpoint;
    private String restateContainerImage = DEFAULT_RESTATE_CONTAINER;
    private final Map<String, String> additionalEnv = new HashMap<>();
    private String configFile;

    Builder(Endpoint endpoint) {
      this.endpoint = endpoint;
    }

    /** Override the container image to use for the Restate runtime. */
    public Builder withRestateContainerImage(String restateContainerImage) {
      this.restateContainerImage = restateContainerImage;
      return this;
    }

    /** Add additional environment variables to the Restate container. */
    public Builder withAdditionalEnv(String key, String value) {
      this.additionalEnv.put(key, value);
      return this;
    }

    /** Mount a config file in the Restate container. */
    public Builder withConfigFile(String configFile) {
      this.configFile = configFile;
      return this;
    }

    /**
     * @return a {@link RestateRunner} to start and stop the test infra manually.
     */
    public RestateRunner build() {
      return new RestateRunner(
          endpoint, this.restateContainerImage, this.additionalEnv, this.configFile);
    }
  }

  /** Run restate, run the embedded service endpoint server, and register the services. */
  public void start() {
    // Start listening the local server
    try {
      server.listen(0).toCompletionStage().toCompletableFuture().get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    // Expose the server port
    int serviceEndpointPort = server.actualPort();
    LOG.debug("Started embedded service endpoint server on port {}", serviceEndpointPort);
    Testcontainers.exposeHostPorts(serviceEndpointPort);

    // Now create the runtime container and deploy it
    this.runtimeContainer.start();
    LOG.debug("Started Restate container");

    // Register services now
    ApiClient client = getAdminClient();
    try {
      RegisterDeploymentResponse response =
          new DeploymentApi(client)
              .createDeployment(
                  new RegisterDeploymentRequest(
                      new RegisterDeploymentRequestAnyOf()
                          .uri("http://host.testcontainers.internal:" + serviceEndpointPort)));
      LOG.debug(
          "Registered services {}",
          response.getServices().stream()
              .map(dev.restate.admin.model.ServiceMetadata::getName)
              .collect(Collectors.toList()));
    } catch (ApiException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Get restate ingress url to send HTTP/gRPC requests to services.
   *
   * @throws IllegalStateException if the restate container is not running.
   */
  public URL getRestateUrl() {
    try {
      return new URL(
          "http",
          runtimeContainer.getHost(),
          runtimeContainer.getMappedPort(RESTATE_INGRESS_ENDPOINT_PORT),
          "/");
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Get restate admin url to send HTTP requests to the admin API.
   *
   * @throws IllegalStateException if the restate container is not running.
   */
  public URL getAdminUrl() {
    try {
      return new URL(
          "http",
          runtimeContainer.getHost(),
          runtimeContainer.getMappedPort(RESTATE_ADMIN_ENDPOINT_PORT),
          "/");
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  /** Get the restate container. */
  public GenericContainer<?> getRestateContainer() {
    return this.runtimeContainer;
  }

  /** Stop restate and the embedded service endpoint server. */
  public void stop() {
    this.close();
  }

  /** Like {@link #stop()}. */
  @Override
  public void close() {
    runtimeContainer.stop();
    LOG.debug("Stopped Restate container");
    server.close().toCompletionStage().toCompletableFuture().join();
    LOG.debug("Stopped Embedded Service endpoint server");
  }

  // -- Methods used by the JUnit5 extension

  ApiClient getAdminClient() {
    return new ApiClient()
        .setHost(runtimeContainer.getHost())
        .setPort(runtimeContainer.getMappedPort(RESTATE_ADMIN_ENDPOINT_PORT));
  }

  URL getIngressUrl() {
    try {
      return new URL(
          "http",
          runtimeContainer.getHost(),
          runtimeContainer.getMappedPort(RESTATE_INGRESS_ENDPOINT_PORT),
          "/");
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
