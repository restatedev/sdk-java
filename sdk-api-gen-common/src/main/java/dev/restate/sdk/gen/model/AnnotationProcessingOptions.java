// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.model;

import java.util.*;

public class AnnotationProcessingOptions {

  private static final String DISABLED_CLIENT_GENERATION =
      "dev.restate.codegen.disabledClientGeneration";

  private final Set<String> disabledClientGenFQCN;

  public AnnotationProcessingOptions(Map<String, String> options) {
    this.disabledClientGenFQCN =
        new HashSet<>(List.of(options.getOrDefault(DISABLED_CLIENT_GENERATION, "").split("[,|]")));
  }

  public boolean isClientGenDisabled(String fqcn) {
    return this.disabledClientGenFQCN.contains(fqcn);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AnnotationProcessingOptions that)) return false;
    return Objects.equals(disabledClientGenFQCN, that.disabledClientGenFQCN);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(disabledClientGenFQCN);
  }

  @Override
  public String toString() {
    return "AnnotationProcessingOptions{" + "disabledClientGenFQCN=" + disabledClientGenFQCN + '}';
  }
}
