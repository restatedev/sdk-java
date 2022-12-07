package dev.restate.sdk.testing.junit5;

import org.junit.jupiter.api.extension.*;

// TODO copy impl from sdk-testing
public class RestateExtension implements ParameterResolver, BeforeEachCallback {

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {}

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return false;
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return null;
  }
}
