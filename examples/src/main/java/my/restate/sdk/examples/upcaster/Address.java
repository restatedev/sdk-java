// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples.upcaster;

import dev.restate.common.Slice;
import dev.restate.sdk.Restate;
import dev.restate.sdk.annotation.CustomSerdeFactory;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.VirtualObject;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.definition.ServiceType;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import dev.restate.sdk.upcasting.Upcaster;
import dev.restate.sdk.upcasting.UpcasterFactory;
import dev.restate.serde.jackson.JacksonSerdeFactory;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Example of using an upcaster to migrate data from an XML-serialized V1 Address to a JSON
 * AddressV2 with a default country field.
 */
@VirtualObject
@CustomSerdeFactory(JacksonSerdeFactory.class) // Configure SerdeFactory to be JacksonSerdeFactory
public class Address {

  private static final StateKey<AddressV2> ADDRESS = StateKey.of("address", AddressV2.class);

  /** Returns the current address or null if not yet set. */
  @Handler
  @Shared
  public AddressV2 get() {
    return Restate.state().get(ADDRESS).orElse(null);
  }

  /** Changes the current address and returns the previous one (or null if none). */
  @Handler
  public AddressV2 change(AddressV2 newAddress) {
    var state = Restate.state();
    AddressV2 previous = state.get(ADDRESS).orElse(null);
    state.set(ADDRESS, newAddress);
    return previous;
  }

  public static void main(String[] args) {
    // Bind the service and configure a custom UpcasterFactory that migrates XML->JSON for AddressV1
    Endpoint endpoint =
        Endpoint.builder()
            .bind(new Address(), cfg -> cfg.configureUpcasterFactory(new AddressUpcasterFactory()))
            .build();

    RestateHttpServer.listen(endpoint);
  }

  /** V1 of the address, previously serialized as XML (city, zipcode, street). */
  public record AddressV1(String city, String zipcode, String street) {}

  /** V2 of the address, JSON with an additional country field. */
  public record AddressV2(String city, String zipcode, String street, String country) {}

  /** A simple UpcasterFactory that provides the AddressXmlToJsonUpcaster for this service. */
  static final class AddressUpcasterFactory implements UpcasterFactory {

    @Override
    public Upcaster newUpcaster(
        String serviceName, ServiceType serviceType, Map<String, String> metadata) {
      // Only attach to this virtual object; harmless if attached elsewhere as canUpcast() filters.
      return new AddressXmlToJsonUpcaster();
    }
  }

  /**
   * Upcaster that detects XML payloads representing {@link AddressV1} and converts them to JSON for
   * {@link AddressV2}, adding a default country of "Serbia". Non-XML payloads are returned
   * unchanged.
   */
  static final class AddressXmlToJsonUpcaster implements Upcaster {

    private static final Pattern CITY = tagPattern("city");
    private static final Pattern ZIP = tagPattern("zipcode");
    private static final Pattern STREET = tagPattern("street");

    private static Pattern tagPattern(String tag) {
      // Very small, naive XML extractor for demo purposes only.
      return Pattern.compile("<" + tag + ">\\s*(.*?)\\s*</" + tag + ">", Pattern.DOTALL);
    }

    @Override
    public boolean canUpcast(Slice body, Map<String, String> headers) {
      // Heuristic: XML payloads usually start with '<' or an XML declaration. Trim leading
      // whitespace.
      String s = sliceToString(body).stripLeading();
      return !s.isEmpty() && s.charAt(0) == '<';
    }

    @Override
    public Slice upcast(Slice body, Map<String, String> headers) {
      String xml = sliceToString(body);
      String city = extract(xml, CITY);
      String zip = extract(xml, ZIP);
      String street = extract(xml, STREET);

      // Build JSON for AddressV2, adding default country = "Serbia" if not provided.
      String json =
          "{"
              + "\"city\":\""
              + escapeJson(city)
              + "\","
              + "\"zipcode\":\""
              + escapeJson(zip)
              + "\","
              + "\"street\":\""
              + escapeJson(street)
              + "\","
              + "\"country\":\"Serbia\""
              + "}";
      return Slice.wrap(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String sliceToString(Slice body) {
      return new String(body.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String extract(String xml, Pattern p) {
      Matcher m = p.matcher(xml);
      return m.find() ? m.group(1) : "";
    }

    private static String escapeJson(String in) {
      if (in == null) return "";
      // Minimal escaping for demo purposes
      return in.replace("\\", "\\\\").replace("\"", "\\\"");
    }
  }
}
