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

public class Handler {

  private final CharSequence name;
  private final HandlerType handlerType;
  private final @Nullable String inputAccept;
  private final PayloadType inputType;
  private final PayloadType outputType;

  public Handler(
      CharSequence name,
      HandlerType handlerType,
      @Nullable String inputAccept,
      PayloadType inputType,
      PayloadType outputType) {
    this.name = name;
    this.handlerType = handlerType;
    this.inputAccept = inputAccept;
    this.inputType = inputType;
    this.outputType = outputType;
  }

  public CharSequence getName() {
    return name;
  }

  public HandlerType getHandlerType() {
    return handlerType;
  }

  public String getInputAccept() {
    return inputAccept;
  }

  public PayloadType getInputType() {
    return inputType;
  }

  public PayloadType getOutputType() {
    return outputType;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private CharSequence name;
    private HandlerType handlerType;
    private PayloadType inputType;
    private PayloadType outputType;

    public Builder withName(CharSequence name) {
      this.name = name;
      return this;
    }

    public Builder withHandlerType(HandlerType handlerType) {
      this.handlerType = handlerType;
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
      String handlerNameLowercase = name.toString().toLowerCase();
      if (handlerNameLowercase.startsWith("restate")
          || handlerNameLowercase.startsWith("openapi")) {
        throw new IllegalArgumentException(
            "A service name cannot start with `restate` or `openapi`");
      }

      return new Handler(
          Objects.requireNonNull(name),
          Objects.requireNonNull(handlerType),
          null,
          inputType,
          outputType);
    }
  }
}
