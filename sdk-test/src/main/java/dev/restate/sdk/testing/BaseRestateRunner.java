package dev.restate.sdk.testing;

import dev.restate.admin.client.ApiClient;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

abstract class BaseRestateRunner implements ParameterResolver {

  static final Namespace NAMESPACE = Namespace.create(BaseRestateRunner.class);
  private static final String MANAGED_CHANNEL_KEY = "ManagedChannelKey";
  static final String DEPLOYER_KEY = "Deployer";

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return (parameterContext.isAnnotated(RestateAdminClient.class)
            && ApiClient.class.isAssignableFrom(parameterContext.getParameter().getType()))
        || (parameterContext.isAnnotated(RestateGrpcChannel.class)
            && ManagedChannel.class.isAssignableFrom(parameterContext.getParameter().getType()))
        || (parameterContext.isAnnotated(RestateURL.class)
                && String.class.isAssignableFrom(parameterContext.getParameter().getType())
            || URL.class.isAssignableFrom(parameterContext.getParameter().getType()));
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    if (parameterContext.isAnnotated(RestateAdminClient.class)) {
      return getDeployer(extensionContext).getAdminClient();
    } else if (parameterContext.isAnnotated(RestateGrpcChannel.class)) {
      return resolveChannel(extensionContext);
    } else if (parameterContext.isAnnotated(RestateURL.class)) {
      URL url = getDeployer(extensionContext).getIngressUrl();
      if (parameterContext.getParameter().getType().equals(String.class)) {
        return url.toString();
      }
      return url;
    }
    throw new ParameterResolutionException("The parameter is not supported");
  }

  private ManagedChannel resolveChannel(ExtensionContext extensionContext) {
    return extensionContext
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            MANAGED_CHANNEL_KEY,
            k -> {
              URL url = getDeployer(extensionContext).getIngressUrl();

              ManagedChannel channel =
                  NettyChannelBuilder.forAddress(url.getHost(), url.getPort())
                      .disableServiceConfigLookUp()
                      .usePlaintext()
                      .build();

              return new ManagedChannelResource(channel);
            },
            ManagedChannelResource.class)
        .channel;
  }

  private ManualRestateRunner getDeployer(ExtensionContext extensionContext) {
    return (ManualRestateRunner) extensionContext.getStore(NAMESPACE).get(DEPLOYER_KEY);
  }

  // AutoCloseable wrapper around ManagedChannelResource
  private static class ManagedChannelResource implements Store.CloseableResource {

    private final ManagedChannel channel;

    private ManagedChannelResource(ManagedChannel channel) {
      this.channel = channel;
    }

    @Override
    public void close() throws Exception {
      // Shutdown channel
      channel.shutdown();
      if (channel.awaitTermination(5, TimeUnit.SECONDS)) {
        return;
      }

      // Force shutdown now
      channel.shutdownNow();
      if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Cannot terminate ManagedChannel on time");
      }
    }
  }
}
