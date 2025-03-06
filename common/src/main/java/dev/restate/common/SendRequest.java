// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common;

import dev.restate.serde.TypeTag;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Object encapsulating request parameters, including the parameters exclusive to one way calls
 * (also referred as send).
 *
 * @param <Req> the request type
 * @param <Res> the response type
 */
public final class SendRequest<Req, Res> extends Request<Req, Res> {

  @Nullable private final Duration delay;

  SendRequest(
      Target target,
      TypeTag<Req> reqTypeTag,
      TypeTag<Res> resTypeTag,
      Req request,
      @Nullable String idempotencyKey,
      @Nullable LinkedHashMap<String, String> headers,
      @Nullable Duration delay) {
    super(target, reqTypeTag, resTypeTag, request, idempotencyKey, headers);
    this.delay = delay;
  }

  public @Nullable Duration delay() {
    return delay;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SendRequest<?, ?> that)) return false;
    return Objects.equals(target(), that.target())
        && Objects.equals(requestTypeTag(), that.requestTypeTag())
        && Objects.equals(responseTypeTag(), that.responseTypeTag())
        && Objects.equals(request(), that.request())
        && Objects.equals(idempotencyKey(), that.idempotencyKey())
        && Objects.equals(headers(), that.headers())
        && Objects.equals(delay, that.delay);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        target(),
        requestTypeTag(),
        responseTypeTag(),
        request(),
        idempotencyKey(),
        headers(),
        delay);
  }

  @Override
  public String toString() {
    return "CallRequest{"
        + "target="
        + target()
        + ", reqSerdeInfo="
        + requestTypeTag()
        + ", resSerdeInfo="
        + responseTypeTag()
        + ", request="
        + request()
        + ", idempotencyKey='"
        + idempotencyKey()
        + '\''
        + ", headers="
        + headers()
        + ", delay="
        + delay
        + '}';
  }
}
