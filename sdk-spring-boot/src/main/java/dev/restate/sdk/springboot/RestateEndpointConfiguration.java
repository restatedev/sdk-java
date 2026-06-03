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
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

@Configuration
@EnableConfigurationProperties({RestateEndpointProperties.class, RestateComponentsProperties.class})
public class RestateEndpointConfiguration {

  /** Language-specific options support, resolved at class-load time. */
  private static final OptionsSupport JAVA_SUPPORT =
      isClassPresent("dev.restate.sdk.HandlerRunner")
          ? loadSupport("dev.restate.sdk.springboot.JavaOptionsSupport")
          : nopOptionsSupport("java");

  private static final OptionsSupport KOTLIN_SUPPORT =
      isClassPresent("dev.restate.sdk.kotlin.HandlerRunner")
          ? loadSupport("dev.restate.sdk.springboot.KotlinOptionsSupport")
          : nopOptionsSupport("kotlin");

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

    // Resolve the ObservationRegistry if present in classpath.
    Object observationRegistry = lookupObservationRegistry(applicationContext);

    // Default executor
    Executor defaultExecutor =
        restateComponentsProperties.getExecutor() != null
            ? applicationContext.getBean(restateComponentsProperties.getExecutor(), Executor.class)
            : null;

    // Build the Endpoint object
    var builder = Endpoint.builder();
    for (var serviceNameAndBean : discoveredRestateComponentBeans.entrySet()) {
      var serviceName = serviceNameAndBean.getKey();
      var restateServiceDefinitionClazz = serviceNameAndBean.getValue().getKey();
      var serviceInstance = serviceNameAndBean.getValue().getValue();
      var isKotlinClass = ReflectionUtils.isKotlinClass(restateServiceDefinitionClazz);

      String specificExecutor = null;

      // Apply global defaults first, per-service config layered on top
      Consumer<ServiceDefinition.Configurator> configurator =
          conf -> configureServiceDefaults(conf, restateComponentsProperties);

      var componentProperties = restateComponentsProperties.getComponents().get(serviceName);
      if (componentProperties != null) {
        if (componentProperties.getExecutor() != null) {
          if (isKotlinClass) {
            throw new IllegalStateException(
                "Configured an executor for service "
                    + serviceName
                    + " implemented with Kotlin. This is currently not supported, you can set the executor only for Restate java components.");
          }
          specificExecutor = componentProperties.getExecutor();
        }
        final var finalComponentProperties = componentProperties;
        configurator =
            combine(configurator, conf -> configureService(conf, finalComponentProperties));
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
            specificExecutor = componentPropertiesBean.getExecutor();
          }
          configurator =
              combine(configurator, conf -> configureService(conf, componentPropertiesBean));
        }
      }

      // Prepare HandlerRunner options
      OptionsSupport support = isKotlinClass ? KOTLIN_SUPPORT : JAVA_SUPPORT;
      HandlerRunner.Options handlerOptions = support.createOptions();
      if (specificExecutor != null) {
        support.setExecutor(
            handlerOptions, applicationContext.getBean(specificExecutor, Executor.class));
      } else if (defaultExecutor != null) {
        support.setExecutor(handlerOptions, defaultExecutor);
      }
      if (observationRegistry != null) {
        support.setMicrometerTracing(handlerOptions, observationRegistry);
      }

      builder.bind(serviceInstance, handlerOptions, configurator);
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

  private static void configureServiceDefaults(
      ServiceDefinition.Configurator configurator, RestateComponentsProperties properties) {
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

  private static OptionsSupport nopOptionsSupport(String module) {
    return new OptionsSupport() {
      @Override
      public HandlerRunner.Options createOptions() {
        throw new IllegalStateException(
            "Could not correctly load handler options for service built using " + module);
      }

      @Override
      public void setExecutor(HandlerRunner.@Nullable Options opts, Executor executor) {}

      @Override
      public void setMicrometerTracing(HandlerRunner.@Nullable Options opts, Object registry) {}
    };
  }

  private static boolean isClassPresent(String name) {
    try {
      Class.forName(name, false, RestateEndpointConfiguration.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      return false;
    }
  }

  private static OptionsSupport loadSupport(String supportClassName) {
    try {
      return (OptionsSupport)
          Class.forName(supportClassName).getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Failed to load OptionsSupport implementation " + supportClassName, e);
    }
  }

  private static @Nullable Object lookupObservationRegistry(ApplicationContext ctx) {
    Class<?> registryClass;
    try {
      registryClass =
          Class.forName(
              "io.micrometer.observation.ObservationRegistry",
              false,
              RestateEndpointConfiguration.class.getClassLoader());
    } catch (ClassNotFoundException | LinkageError e) {
      return null;
    }
    try {
      return ctx.getBean(registryClass);
    } catch (NoSuchBeanDefinitionException e) {
      return null;
    }
  }
}
