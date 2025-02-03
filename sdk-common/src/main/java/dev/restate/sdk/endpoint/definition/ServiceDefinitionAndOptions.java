package dev.restate.sdk.endpoint.definition;

/**
 * Container of {@link ServiceDefinition} and its options.
 */
public record ServiceDefinitionAndOptions<O>(ServiceDefinition<O> service, O options) {
}
