// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record Handler(
    String name,
    String restateName,
    HandlerType handlerType,
    @Nullable String inputAccept,
    PayloadType inputType,
    PayloadType outputType,
    @Nullable String documentation) {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String name;
    private String restateName;
    private HandlerType handlerType;
    private String inputAccept;
    private PayloadType inputType;
    private PayloadType outputType;
    private String documentation;

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withRestateName(String name) {
      this.restateName = name;
      return this;
    }

    public Builder withHandlerType(HandlerType handlerType) {
      this.handlerType = handlerType;
      return this;
    }

    public Builder withInputAccept(String inputAccept) {
      this.inputAccept = inputAccept;
      return this;
    }

    public Builder withInputType(PayloadType inputType) {
      this.inputType = inputType;
      return this;
    }

    public Builder withOutputType(PayloadType outputType) {
      this.outputType = outputType;
      return this;
    }

    public Builder withDocumentation(String documentation) {
      this.documentation = documentation;
      return this;
    }

    public CharSequence getName() {
      return name;
    }

    public HandlerType getHandlerType() {
      return handlerType;
    }

    public PayloadType getInputType() {
      return inputType;
    }

    public PayloadType getOutputType() {
      return outputType;
    }

    public Handler validateAndBuild() {
      String restateName =
          this.restateName != null ? this.restateName : Objects.requireNonNull(name);

      String handlerNameLowercase = restateName.toLowerCase();
      if (restateName.startsWith("restate") || handlerNameLowercase.startsWith("openapi")) {
        throw new IllegalArgumentException(
            "A service name cannot start with `restate` or `openapi`");
      }

      return new Handler(
          Objects.requireNonNull(name),
          restateName,
          Objects.requireNonNull(handlerType),
          inputAccept,
          inputType,
          outputType,
          documentation);
    }
  }
}
