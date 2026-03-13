// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples;

import dev.restate.sdk.Restate;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;

public class ConcurrencyLimitExample {

  // --- Amazon Merchant Service: a third-party API wrapper ---

  @Service
  public static class AmazonMerchantService {

    public record CheckoutRequest(String orderId, String productId, int quantity) {}

    public record CheckoutResponse(String confirmationId) {}

    @Handler
    public CheckoutResponse checkout(CheckoutRequest req) {
      // In a real app, this would call the Amazon Merchant API
      // using the user-provided API key from the scope.
      return new CheckoutResponse("conf-" + req.orderId);
    }
  }

  // --- Order Processor: uses scoped calls to rate-limit per API key ---

  @Service
  public static class OrderProcessor {

    public record ProcessOrderRequest(String orderId, String amazonApiKey) {}

    /**
     * Process an order by calling AmazonMerchantService within a scope keyed by the user's Amazon
     * API key.
     *
     * <p>The scope + configured rate limit rules ensure that calls sharing the same API key are
     * rate-limited (e.g. 10 requests every 2 hours), preventing us from exceeding the third-party
     * API quota.
     *
     * <p>Rate limit rules are configured externally in Restate:
     *
     * <pre>{@code
     * // Default rule: on any scope, rate limit AmazonMerchantService to 10 req / 2h
     * {
     *   "scope": { "any": true },
     *   "match": { "service": "AmazonMerchantService" },
     *   "limit": {
     *     "rateLimit": { "count": 10, "interval": { "hours": 2 } }
     *   }
     * }
     *
     * // Override for a specific API key: increase limit to 100 req / 2h
     * {
     *   "scope": { "equals": "amz-api-key-123" },
     *   "match": { "service": "AmazonMerchantService" },
     *   "limit": {
     *     "rateLimit": { "count": 100, "interval": { "hours": 2 } }
     *   }
     * }
     * }</pre>
     */
    @Handler
    public String processOrder(ProcessOrderRequest req) {
      // Scope the call by the user's Amazon API key.
      // Restate enforces the rate limit rules configured above.
      var response =
          Restate.scope(req.amazonApiKey)
              .service(AmazonMerchantService.class)
              .checkout(new AmazonMerchantService.CheckoutRequest(req.orderId, "product-42", 1));

      return "Order " + req.orderId + " confirmed: " + response.confirmationId();
    }
  }

  public static void main(String[] args) {
    RestateHttpServer.listen(Endpoint.bind(new AmazonMerchantService()).bind(new OrderProcessor()));
  }
}
