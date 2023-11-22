// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.syscalls;

import com.google.protobuf.ByteString;
import dev.restate.sdk.core.TerminalException;

public interface ExitSideEffectSyscallCallback extends SyscallCallback<ByteString> {

  /** This is user failure. */
  void onFailure(TerminalException t);
}
