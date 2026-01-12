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

  public static final String DISABLED_CLIENT_GENERATION =
      "dev.restate.codegen.disabledClientGeneration";

  public static final String DISABLED_CLASSES = "dev.restate.codegen.disabledClasses";

  private final Set<String> disabledClientGenFQCN;
  private final Set<String> disabledClasses;

  public AnnotationProcessingOptions(Map<String, String> options) {
    this.disabledClientGenFQCN =
        new HashSet<>(List.of(options.getOrDefault(DISABLED_CLIENT_GENERATION, "").split("[,|]")));
    this.disabledClasses =
        new HashSet<>(List.of(options.getOrDefault(DISABLED_CLASSES, "").split("[,|]")));
  }

  public boolean isClientGenDisabled(String fqcn) {
    return this.disabledClientGenFQCN.contains(fqcn);
  }

  public boolean isClassDisabled(String fqcn) {
    return this.disabledClasses.contains(fqcn);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AnnotationProcessingOptions that)) return false;
    return Objects.equals(disabledClientGenFQCN, that.disabledClientGenFQCN)
        && Objects.equals(disabledClasses, that.disabledClasses);
  }

  @Override
  public int hashCode() {
    return Objects.hash(disabledClientGenFQCN, disabledClasses);
  }

  @Override
  public String toString() {
    return "AnnotationProcessingOptions{"
        + "disabledClientGenFQCN="
        + disabledClientGenFQCN
        + ", disabledClasses="
        + disabledClasses
        + '}';
  }
}
