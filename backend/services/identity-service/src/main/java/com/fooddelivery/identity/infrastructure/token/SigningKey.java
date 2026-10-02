package com.fooddelivery.identity.infrastructure.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * The RSA key that signs access tokens (RS256, docs/09-security.md §2.2). The key ID is the RFC
 * 7638 thumbprint, so every instance that loads the same key publishes the same {@code kid}.
 */
public final class SigningKey {

  static final int MIN_BITS = 2048;
  private static final String PEM_BEGIN = "-----BEGIN PRIVATE KEY-----";
  private static final String PEM_END = "-----END PRIVATE KEY-----";

  private final RSAKey jwk;
  private final boolean ephemeral;

  private SigningKey(RSAKey jwk, boolean ephemeral) {
    this.jwk = jwk;
    this.ephemeral = ephemeral;
  }

  /** Loads an unencrypted PKCS#8 PEM ({@code openssl genpkey -algorithm RSA}). */
  public static SigningKey fromPkcs8Pem(String pem) {
    String body = pem.strip();
    if (!body.startsWith(PEM_BEGIN) || !body.endsWith(PEM_END)) {
      throw new IllegalStateException("The JWT signing key must be an unencrypted PKCS#8 PEM.");
    }
    body = body.substring(PEM_BEGIN.length(), body.length() - PEM_END.length());
    try {
      byte[] der = Base64.getMimeDecoder().decode(body);
      KeyFactory factory = KeyFactory.getInstance("RSA");
      if (!(factory.generatePrivate(new PKCS8EncodedKeySpec(der))
          instanceof RSAPrivateCrtKey privateKey)) {
        throw new IllegalStateException("The JWT signing key must be an RSA private key.");
      }
      RSAPublicKey publicKey =
          (RSAPublicKey)
              factory.generatePublic(
                  new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
      return new SigningKey(build(publicKey, privateKey), false);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("The JWT signing key could not be read.", e);
    }
  }

  /** A throwaway key for a developer machine; every token becomes invalid on restart. */
  public static SigningKey generateEphemeral() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(MIN_BITS);
      KeyPair pair = generator.generateKeyPair();
      return new SigningKey(
          build((RSAPublicKey) pair.getPublic(), (RSAPrivateCrtKey) pair.getPrivate()), true);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not generate an RSA key", e);
    }
  }

  private static RSAKey build(RSAPublicKey publicKey, RSAPrivateCrtKey privateKey) {
    if (publicKey.getModulus().bitLength() < MIN_BITS) {
      throw new IllegalStateException(
          "The JWT signing key must be at least " + MIN_BITS + " bits long.");
    }
    try {
      return new RSAKey.Builder(publicKey)
          .privateKey(privateKey)
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(JWSAlgorithm.RS256)
          .keyIDFromThumbprint()
          .build();
    } catch (JOSEException e) {
      throw new IllegalStateException("Could not derive the key ID", e);
    }
  }

  public String kid() {
    return jwk.getKeyID();
  }

  /** Includes the private key; never serialise it. */
  public RSAKey jwk() {
    return jwk;
  }

  public String publicJwkJson() {
    return jwk.toPublicJWK().toJSONString();
  }

  public boolean ephemeral() {
    return ephemeral;
  }
}
