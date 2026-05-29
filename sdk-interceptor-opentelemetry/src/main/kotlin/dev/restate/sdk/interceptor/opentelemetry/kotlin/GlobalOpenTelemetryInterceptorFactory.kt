// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.opentelemetry.kotlin

import dev.restate.sdk.kotlin.interceptor.HandlerInterceptor
import dev.restate.sdk.kotlin.interceptor.RunInterceptor
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry

/** SPI default using [GlobalOpenTelemetry]. */
class GlobalOpenTelemetryInterceptorFactory : HandlerInterceptor.Factory, RunInterceptor.Factory {

  override fun createHandlerInterceptor(): HandlerInterceptor? {
    val otel = GlobalOpenTelemetry.get()
    if (otel == OpenTelemetry.noop()) return null
    return OpenTelemetryInterceptorFactory(otel).createHandlerInterceptor()
  }

  override fun createRunInterceptor(): RunInterceptor? {
    val otel = GlobalOpenTelemetry.get()
    if (otel == OpenTelemetry.noop()) return null
    return OpenTelemetryInterceptorFactory(otel).createRunInterceptor()
  }
}
