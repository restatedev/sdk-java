// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.common.reflections.ReflectionUtils;
import dev.restate.sdk.auth.signing.RestateRequestIdentityVerifier;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.definition.HandlerDefinition;
import dev.restate.sdk.endpoint.definition.HandlerRunner;
import dev.restate.sdk.endpoint.definition.InvocationRetryPolicy;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

@Configuration
@EnableConfigurationProperties({RestateEndpointProperties.class, RestateComponentsProperties.class})
public class RestateEndpointConfiguration {

  // Cached reflection method for HandlerRunner.Options.withExecutor(Executor)
  private static final Method WITH_EXECUTOR_METHOD;

  static {
    Method method;
    try {
      Class<?> optionsClass = Class.forName("dev.restate.sdk.HandlerRunner$Options");
      method = optionsClass.getMethod("withExecutor", Executor.class);
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      // Leave it null, it will fail if being used
      method = null;
    }
    WITH_EXECUTOR_METHOD = method;
  }

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @SuppressWarnings("deprecation")
  @Bean
  public @Nullable Endpoint restateEndpoint(
      ApplicationContext applicationContext,
      RestateEndpointProperties restateEndpointProperties,
      RestateComponentsProperties restateComponentsProperties) {
    // Adapt beans to ServiceDefinition
    var beansToAdapt = applicationContext.getBeansWithAnnotation(RestateComponent.class);
    Map<String, Map.Entry<Class<?>, Object>> discoveredRestateComponentBeans =
        new HashMap<>(beansToAdapt.size());
    if (!beansToAdapt.isEmpty()) {
      for (var bean : beansToAdapt.values()) {
        var restateServiceDefinitionClazz =
            ReflectionUtils.findRestateAnnotatedClass(bean.getClass());
        String restateName = ReflectionUtils.extractServiceName(restateServiceDefinitionClazz);
        discoveredRestateComponentBeans.put(
            restateName, Map.entry(restateServiceDefinitionClazz, bean));
      }
    }

    if (discoveredRestateComponentBeans.isEmpty()) {
      logger.info("No restate function/service/virtual object/workflow was found.");
      // Don't start anything if no service is registered
      return null;
    } else {
      logger.info(
          "Registering Restate components: {}",
          discoveredRestateComponentBeans.keySet().stream()
              .collect(Collectors.joining(", ", "[", "]")));
    }

    // Create default handler runner options, if available
    HandlerRunner.Options javaRunnerOptions =
        createHandlerRunnerOptions(applicationContext, restateComponentsProperties.getExecutor());

    // Build the Endpoint object
    var builder = Endpoint.builder();
    for (var serviceNameAndBean : discoveredRestateComponentBeans.entrySet()) {
      var serviceName = serviceNameAndBean.getKey();
      var restateServiceDefinitionClazz = serviceNameAndBean.getValue().getKey();
      var serviceInstance = serviceNameAndBean.getValue().getValue();
      var isKotlinClass = ReflectionUtils.isKotlinClass(restateServiceDefinitionClazz);

      var handlerOptions = javaRunnerOptions;
      Consumer<ServiceDefinition.Configurator> configurator = conf -> {};

      var componentProperties = restateComponentsProperties.getComponents().get(serviceName);
      if (componentProperties != null) {
        if (componentProperties.getExecutor() != null) {
          if (isKotlinClass) {
            throw new IllegalStateException(
                "Configured an executor for service "
                    + serviceName
                    + " implemented with Kotlin. This is currently not supported, you can set the executor only for Restate java components.");
          }
          handlerOptions =
              createHandlerRunnerOptions(applicationContext, componentProperties.getExecutor());
        }
        configurator = conf -> configureService(conf, componentProperties);
      }

      // Check the configurator on the annotation as well
      RestateComponent restateComponent =
          AnnotatedElementUtils.findMergedAnnotation(
              restateServiceDefinitionClazz, RestateComponent.class);
      if (restateComponent != null && !restateComponent.configuration().isEmpty()) {
        var configurationBean = applicationContext.getBean(restateComponent.configuration());
        if (configurationBean instanceof RestateServiceConfigurator) {
          configurator = combine(configurator, (RestateServiceConfigurator) configurationBean);
        } else if (configurationBean
            instanceof RestateComponentProperties componentPropertiesBean) {
          if (componentPropertiesBean.getExecutor() != null) {
            if (isKotlinClass) {
              throw new IllegalStateException(
                  "Configured an executor for service "
                      + serviceName
                      + " implemented with Kotlin. This is currently not supported, you can set the executor only for Restate java components.");
            }
            handlerOptions =
                createHandlerRunnerOptions(
                    applicationContext, componentPropertiesBean.getExecutor());
          }
          configurator =
              combine(configurator, conf -> configureService(conf, componentPropertiesBean));
        }
      }

      builder.bind(serviceInstance, isKotlinClass ? null : handlerOptions, configurator);
    }
    if (restateEndpointProperties.isEnablePreviewContext()) {
      builder = builder.enablePreviewContext();
    }
    if (restateEndpointProperties.getIdentityKey() != null) {
      builder.withRequestIdentityVerifier(
          RestateRequestIdentityVerifier.fromKey(restateEndpointProperties.getIdentityKey()));
    }
    return builder.build();
  }

  /**
   * Applies the configuration from {@link RestateComponentProperties} to a {@link
   * ServiceDefinition.Configurator}.
   *
   * @param configurator the service configurator to configure
   * @param properties the properties to apply
   */
  public static void configureService(
      ServiceDefinition.Configurator configurator, RestateComponentProperties properties) {
    if (properties.getDocumentation() != null) {
      configurator.documentation(properties.getDocumentation());
    }
    if (properties.getMetadata() != null) {
      configurator.metadata(properties.getMetadata());
    }
    if (properties.getInactivityTimeout() != null) {
      configurator.inactivityTimeout(properties.getInactivityTimeout());
    }
    if (properties.getAbortTimeout() != null) {
      configurator.abortTimeout(properties.getAbortTimeout());
    }
    if (properties.getIdempotencyRetention() != null) {
      configurator.idempotencyRetention(properties.getIdempotencyRetention());
    }
    if (properties.getWorkflowRetention() != null) {
      configurator.workflowRetention(properties.getWorkflowRetention());
    }
    if (properties.getJournalRetention() != null) {
      configurator.journalRetention(properties.getJournalRetention());
    }
    if (properties.getIngressPrivate() != null) {
      configurator.ingressPrivate(properties.getIngressPrivate());
    }
    if (properties.getEnableLazyState() != null) {
      configurator.enableLazyState(properties.getEnableLazyState());
    }
    if (properties.getRetryPolicy() != null) {
      configurator.invocationRetryPolicy(convertRetryPolicy(properties.getRetryPolicy()));
    }
    if (properties.getHandlers() != null) {
      for (var entry : properties.getHandlers().entrySet()) {
        configurator.configureHandler(entry.getKey(), hc -> configureHandler(hc, entry.getValue()));
      }
    }
  }

  private static void configureHandler(
      HandlerDefinition.Configurator configurator, RestateHandlerProperties properties) {
    if (properties.getDocumentation() != null) {
      configurator.documentation(properties.getDocumentation());
    }
    if (properties.getMetadata() != null) {
      configurator.metadata(properties.getMetadata());
    }
    if (properties.getInactivityTimeout() != null) {
      configurator.inactivityTimeout(properties.getInactivityTimeout());
    }
    if (properties.getAbortTimeout() != null) {
      configurator.abortTimeout(properties.getAbortTimeout());
    }
    if (properties.getIdempotencyRetention() != null) {
      configurator.idempotencyRetention(properties.getIdempotencyRetention());
    }
    if (properties.getWorkflowRetention() != null) {
      configurator.workflowRetention(properties.getWorkflowRetention());
    }
    if (properties.getJournalRetention() != null) {
      configurator.journalRetention(properties.getJournalRetention());
    }
    if (properties.getIngressPrivate() != null) {
      configurator.ingressPrivate(properties.getIngressPrivate());
    }
    if (properties.getEnableLazyState() != null) {
      configurator.enableLazyState(properties.getEnableLazyState());
    }
    if (properties.getRetryPolicy() != null) {
      configurator.invocationRetryPolicy(convertRetryPolicy(properties.getRetryPolicy()));
    }
  }

  private static InvocationRetryPolicy convertRetryPolicy(RetryPolicyProperties properties) {
    var builder = InvocationRetryPolicy.builder();
    if (properties.getInitialInterval() != null) {
      builder.initialInterval(properties.getInitialInterval());
    }
    if (properties.getExponentiationFactor() != null) {
      builder.exponentiationFactor(properties.getExponentiationFactor());
    }
    if (properties.getMaxInterval() != null) {
      builder.maxInterval(properties.getMaxInterval());
    }
    if (properties.getMaxAttempts() != null) {
      builder.maxAttempts(properties.getMaxAttempts());
    }
    if (properties.getOnMaxAttempts() != null) {
      builder.onMaxAttempts(
          switch (properties.getOnMaxAttempts()) {
            case PAUSE -> InvocationRetryPolicy.OnMaxAttempts.PAUSE;
            case KILL -> InvocationRetryPolicy.OnMaxAttempts.KILL;
          });
    }
    return builder.build();
  }

  private static <T> Consumer<T> combine(Consumer<T> consumer1, Consumer<T> consumer2) {
    return (T t) -> {
      consumer1.accept(t);
      consumer2.accept(t);
    };
  }

  private static HandlerRunner.@Nullable Options createHandlerRunnerOptions(
      ApplicationContext applicationContext, @Nullable String beanExecutorName) {
    if (beanExecutorName == null) {
      return null;
    }
    if (WITH_EXECUTOR_METHOD == null) {
      throw new IllegalStateException(
          "sdk-api module not found. The executor option is only supported for Java services.");
    }
    Executor executor = applicationContext.getBean(beanExecutorName, Executor.class);

    try {
      return (HandlerRunner.Options) WITH_EXECUTOR_METHOD.invoke(null, executor);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to create HandlerRunner.Options", e);
    }
  }
}
