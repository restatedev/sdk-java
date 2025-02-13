// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client.base;

import com.fasterxml.jackson.core.JsonFactory;
import dev.restate.client.Client;
import java.net.URI;
import java.util.Map;

/** Base client. This can be used to easily build {@link Client} implementations on top */
public abstract class BaseClient implements Client {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private final URI baseUri;
  private final Map<String, String> headers;

  protected BaseClient(URI baseUri, Map<String, String> headers) {
    this.baseUri = baseUri;
    this.headers = headers;
  }
}
