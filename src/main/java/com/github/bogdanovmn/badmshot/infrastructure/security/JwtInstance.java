package com.github.bogdanovmn.badmshot.infrastructure.security;

import com.github.bogdanovmn.badmshot.infrastructure.security.common.FileResource;
import com.github.bogdanovmn.badmshot.infrastructure.security.common.RSAKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.PublicKey;

@Component
@Slf4j
class JwtInstance {
	private final PublicKey publicKey;

	public JwtInstance(@Value("${jwt.public-key-path:}") String publicKeyPath) throws IOException {
		log.info("JWT public key loading: {}", publicKeyPath);
		publicKey = RSAKey.ofDER(
			new FileResource(publicKeyPath).content()
		).asPublicKey();
	}

	public Jws<Claims> checkSignatureAndReturnClaims(String token) {
		return Jwts.parserBuilder()
			.setSigningKey(publicKey)
			.build()
			.parseClaimsJws(token);
	}
}
