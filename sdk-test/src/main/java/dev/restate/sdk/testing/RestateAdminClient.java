package dev.restate.sdk.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inject the Restate {@link dev.restate.admin.client.ApiClient}, useful to build admin clients,
 * such as {@link dev.restate.admin.api.ServiceEndpointApi}.
 */
@Target(value = ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestateAdminClient {}
