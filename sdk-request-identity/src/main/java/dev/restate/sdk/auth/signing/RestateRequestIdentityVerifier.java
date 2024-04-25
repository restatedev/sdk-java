// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.auth.signing;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.SignedJWT;
import dev.restate.sdk.auth.RequestIdentityVerifier;
import java.util.Base64;

public class RestateRequestIdentityVerifier implements RequestIdentityVerifier {
  private static final String SIGNATURE_SCHEME_HEADER = "x-restate-signature-scheme";
  private static final String SIGNATURE_SCHEME_V1 = "v1";
  private static final String SIGNATURE_SCHEME_UNSIGNED = "unsigned";
  private static final String JWT_HEADER = "x-restate-jwt-v1";
  private static final String IDENTITY_V1_PREFIX = "publickeyv1_";
  private static final byte[] ASN1_PREFIX = Base64.getDecoder().decode("MCowBQYDK2VwAyEA");

  private final JWSVerifier verifier;

  private RestateRequestIdentityVerifier(JWSVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  public void verifyRequest(Headers headers) throws Exception {
    String signatureScheme = expectHeader(headers, SIGNATURE_SCHEME_HEADER);
    switch (signatureScheme) {
      case SIGNATURE_SCHEME_V1:
        String jwtHeader = expectHeader(headers, JWT_HEADER);
        SignedJWT signedJWT = SignedJWT.parse(jwtHeader);
        if (!signedJWT.verify(verifier)) {
          throw new IllegalStateException("Verification of JWT token failed");
        }
        break;
      case SIGNATURE_SCHEME_UNSIGNED:
        throw new IllegalStateException("Request has no identity, but one was expected");
      default:
        throw new IllegalStateException("Unexpected signature scheme " + signatureScheme);
    }
  }

  private String expectHeader(Headers headers, String key) {
    String value = headers.get(key);
    if (value == null) {
      throw new IllegalArgumentException("Missing header " + key);
    }
    return value;
  }

  /** Create the {@link RequestIdentityVerifier} from Identity v1 strings. */
  public static RequestIdentityVerifier fromIdentityV1Key(String key) {
    if (!key.startsWith(IDENTITY_V1_PREFIX)) {
      throw new IllegalArgumentException(
          "Identity v1 jwt public keys are expected to start with " + IDENTITY_V1_PREFIX);
    }

    byte[] decoded = Base58.decode(key.substring(IDENTITY_V1_PREFIX.length()));
    byte[] finalKey = prependASN1Prefix(decoded);

    OctetKeyPair jwk = new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(finalKey)).build();
    OctetKeyPair publicJWK = jwk.toPublicJWK();
    JWSVerifier verifier;
    try {
      verifier = new Ed25519Verifier(publicJWK);
    } catch (JOSEException e) {
      throw new RuntimeException("Cannot create the verifier", e);
    }

    return new RestateRequestIdentityVerifier(verifier);
  }

  private static byte[] prependASN1Prefix(byte[] y) {
    byte[] combined = new byte[RestateRequestIdentityVerifier.ASN1_PREFIX.length + y.length];
    System.arraycopy(
        RestateRequestIdentityVerifier.ASN1_PREFIX,
        0,
        combined,
        0,
        RestateRequestIdentityVerifier.ASN1_PREFIX.length);
    System.arraycopy(y, 0, combined, RestateRequestIdentityVerifier.ASN1_PREFIX.length, y.length);
    return combined;
  }
}
