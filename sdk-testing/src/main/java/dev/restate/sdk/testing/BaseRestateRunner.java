// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import dev.restate.admin.client.ApiClient;
import dev.restate.sdk.client.IngressClient;
import java.net.URL;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

abstract class BaseRestateRunner implements ParameterResolver {

  static final Namespace NAMESPACE = Namespace.create(BaseRestateRunner.class);
  static final String DEPLOYER_KEY = "Deployer";

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return (parameterContext.isAnnotated(RestateAdminClient.class)
            && ApiClient.class.isAssignableFrom(parameterContext.getParameter().getType()))
        || (parameterContext.isAnnotated(RestateIngressClient.class)
            && IngressClient.class.isAssignableFrom(parameterContext.getParameter().getType()))
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
    } else if (parameterContext.isAnnotated(RestateIngressClient.class)) {
      return resolveIngressClient(extensionContext);
    } else if (parameterContext.isAnnotated(RestateURL.class)) {
      URL url = getDeployer(extensionContext).getIngressUrl();
      if (parameterContext.getParameter().getType().equals(String.class)) {
        return url.toString();
      }
      return url;
    }
    throw new ParameterResolutionException("The parameter is not supported");
  }

  private IngressClient resolveIngressClient(ExtensionContext extensionContext) {
    URL url = getDeployer(extensionContext).getIngressUrl();
    return IngressClient.defaultClient(url.toString());
  }

  private ManualRestateRunner getDeployer(ExtensionContext extensionContext) {
    return (ManualRestateRunner) extensionContext.getStore(NAMESPACE).get(DEPLOYER_KEY);
  }
}
