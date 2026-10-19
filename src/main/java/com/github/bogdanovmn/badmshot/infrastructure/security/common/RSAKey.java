package com.github.bogdanovmn.badmshot.infrastructure.security.common;


import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class RSAKey {
	private final byte[] keyData;

	private RSAKey(byte[] keyData) {
		this.keyData = keyData;
	}

	public static RSAKey ofDER(byte[] keyDataInDerFormat) {
		return new RSAKey(keyDataInDerFormat);
	}

	public PrivateKey asPrivateKey() {
		try {
			return KeyFactory.getInstance("RSA")
				.generatePrivate(
					new PKCS8EncodedKeySpec(keyData)
				);
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public PublicKey asPublicKey() {
		try {
			return KeyFactory.getInstance("RSA")
				.generatePublic(
					new X509EncodedKeySpec(keyData)
				);
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
