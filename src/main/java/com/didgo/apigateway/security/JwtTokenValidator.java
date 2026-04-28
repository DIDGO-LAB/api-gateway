package com.didgo.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenValidator {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public Long validateAccessTokenAndExtractUserId(String token) {
        // 보호 API에는 Access Token만 허용하고 Refresh Token은 명시적으로 거부한다.
        Claims claims = parseClaims(token);
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!TokenType.ACCESS.name().equals(tokenType)) {
            throw new InvalidTokenException("INVALID_TOKEN", "Invalid token.");
        }
        return Long.parseLong(claims.getSubject());
    }

    private Claims parseClaims(String token) {
        try {
            // 서명과 issuer를 함께 검증해 다른 서비스가 발급한 토큰이나 변조 토큰을 차단한다.
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(jwtProperties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException exception) {
            throw new InvalidTokenException("EXPIRED_TOKEN", "Expired token.");
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException("INVALID_TOKEN", "Invalid token.");
        }
    }
}
