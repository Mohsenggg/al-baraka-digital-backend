package com.mgh.backend.auth.security.service;

import com.mgh.backend.auth.domain.dto.TokenExpiryDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private final String secretKey;
    private final long jwtExpiration;


    public JwtService(@Value("${jwt.expiration}") long jwtExpiration) {
        try {

            this.jwtExpiration = jwtExpiration;

            KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
            SecretKey sk = keyGen.generateKey();
            secretKey = Base64.getEncoder().encodeToString(sk.getEncoded());

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    // ================================================================
    // 1 || Generate New Token ----------------------------------------
    // ================================================================

    public TokenExpiryDto generateToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, jwtExpiration);
    }

    private TokenExpiryDto buildToken(Map<String, Object> extraClaims,
                                       UserDetails userDetails,
                                       long expiration) {
        long now = System.currentTimeMillis();
        long expiryMillis = now + jwtExpiration;

        String token = Jwts.builder()
                .setClaims(new HashMap<>())
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(expiryMillis))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();


        return new TokenExpiryDto(token, Instant.ofEpochMilli(expiryMillis));

    }
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes); // ✅ Correct method
    }


    // ================================================================
    // 2 || Validate Existed Token ------------------------------------
    // ================================================================

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    // ================================================================

    private boolean isTokenExpired(String token) {

        return extractExpiration(token).before(new Date());
    }

    public String extractUsername(String token) {

        return extractClaim(token, Claims::getSubject);

    }

    private Date extractExpiration(String token) {

        return extractClaim(token, Claims::getExpiration);
    }

    // ---------------------------------------------------------

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // ================================================================




}