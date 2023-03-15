package dev.restate.sdk.lambda;

/**
 * You need to implement this SPI and register it, to load your Restate services
 *
 * <p>Example implementation:
 *
 * <pre>
 * package com.example;
 *
 * public class MyRestateServerFactory implements LambdaRestateServerFactory {
 *     public LambdaRestateServer create() {
 *         return LambdaRestateServer.builder()
 *                 .withService(new CounterService())
 *                 .build();
 *     }
 * }
 * </pre>
 *
 * <p>To register it, add a file to
 * META-INF/services/dev.restate.sdk.lambda.LambdaRestateServerFactory with the following content:
 *
 * <pre>
 * com.example.MyRestateServerFactory
 * </pre>
 */
public interface LambdaRestateServerFactory {
  LambdaRestateServer create();
}
