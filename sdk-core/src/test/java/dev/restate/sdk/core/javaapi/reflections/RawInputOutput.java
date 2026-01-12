// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi.reflections;

import dev.restate.sdk.Restate;
import dev.restate.sdk.annotation.*;

@Service
@Name("RawInputOutput")
public class RawInputOutput {

  @Handler
  @Raw
  public byte[] rawOutput() {
    return Restate.service(RawInputOutput.class).rawOutput();
  }

  @Handler
  @Raw(contentType = "application/vnd.my.custom")
  public byte[] rawOutputWithCustomCT() {
    return Restate.service(RawInputOutput.class).rawOutputWithCustomCT();
  }

  @Handler
  public void rawInput(@Raw byte[] input) {
    Restate.service(RawInputOutput.class).rawInput(input);
  }

  @Handler
  public void rawInputWithCustomCt(@Raw(contentType = "application/vnd.my.custom") byte[] input) {
    Restate.service(RawInputOutput.class).rawInputWithCustomCt(input);
  }

  @Handler
  public void rawInputWithCustomAccept(
      @Accept("application/*") @Raw(contentType = "application/vnd.my.custom") byte[] input) {
    Restate.service(RawInputOutput.class).rawInputWithCustomAccept(input);
  }
}
