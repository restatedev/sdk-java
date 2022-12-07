package dev.restate.sdk.core.impl;

import io.grpc.Metadata;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

final class Tracing {

  private Tracing() {}

  static TextMapGetter<Metadata> OTEL_TEXT_MAP_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Metadata carrier) {
          return carrier.keys();
        }

        @Nullable
        @Override
        public String get(@Nullable Metadata carrier, String key) {
          if (carrier == null) {
            return null;
          }
          return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
        }
      };

  static AttributeKey<String> RESTATE_INVOCATION_ID =
      AttributeKey.stringKey("restate.invocation.id");

  static AttributeKey<String> RESTATE_STATE_KEY = AttributeKey.stringKey("restate.state.key");
  static AttributeKey<Long> RESTATE_SLEEP_DURATION = AttributeKey.longKey("restate.sleep.duration");

  static AttributeKey<String> RESTATE_COORDINATION_CALL_SERVICE =
      AttributeKey.stringKey("restate.coordination.call.service");
  static AttributeKey<String> RESTATE_COORDINATION_CALL_METHOD =
      AttributeKey.stringKey("restate.coordination.call.method");
}
