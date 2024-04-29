// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
import dev.restate.sdk.auth.signing.RestateRequestIdentityVerifier;
import org.junit.jupiter.api.Test;

public class RestateRequestIdentityVerifierTest {

  @Test
  void parseKey() {
    RestateRequestIdentityVerifier.fromKey(
        "publickeyv1_ChjENKeMvCtRnqG2mrBK1HmPKufgFUc98K8B3ononQvp");
  }
}
