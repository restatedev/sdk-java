package dev.restate.sdk.springboot;

import dev.restate.sdk.auth.signing.RestateRequestIdentityVerifier;
import dev.restate.sdk.endpoint.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.util.Map;

@Configuration
@EnableConfigurationProperties({RestateEndpointProperties.class})
public class RestateEndpointConfiguration {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Bean
    public @Nullable Endpoint restateEndpoint(
             ApplicationContext applicationContext,
     RestateEndpointProperties restateEndpointProperties
    ) {
        Map<String, Object> restateComponents =
                applicationContext.getBeansWithAnnotation(RestateComponent.class);

        if (restateComponents.isEmpty()) {
            logger.info("No @RestateComponent discovered");
            // Don't start anything if no service is registered
            return null;
        }

        var builder = Endpoint.builder();
        for (var componentEntry : restateComponents.entrySet()) {
            // Get configurator, if any
            RestateComponent restateComponent =
                    applicationContext.findAnnotationOnBean(componentEntry.getKey(), RestateComponent.class);
            RestateServiceConfigurator configurator = c -> {};
            if (restateComponent != null && !restateComponent.configuration().isEmpty()) {
                configurator =
                        applicationContext.getBean(
                                restateComponent.configuration(), RestateServiceConfigurator.class);
            }
            builder = builder.bind(componentEntry.getValue(), configurator);
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

}
