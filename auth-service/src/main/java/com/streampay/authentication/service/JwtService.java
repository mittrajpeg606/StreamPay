package com.streampay.authentication.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;

@Service
public class JwtService {

    private SecretKey secretKey;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;



    public JwtService(@Value("${jwt.secret}") String secret,@Value("${jwt.access-token-expiration}") long accessExpiration,@Value("${jwt.refresh-token-expiration}") long refreshExpiration)
    {
        this.secretKey= Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration=accessExpiration;
        this.refreshTokenExpiration=refreshExpiration;
    }

    public String generateAccessToken(String email,String role){
        return generateToken(email,role,accessTokenExpiration);
    }

    public String generateRefreshToken(String email,String role){
        return generateToken(email,role,refreshTokenExpiration);
    }

    private String generateToken(String email, String role, long expiration) {

        Date now=new Date();
        Date expiry=new Date(now.getTime()+expiration);

        HashMap<String,String> claims=new HashMap<>();
        claims.put("email",email);
        if(role!=null)
        {
            claims.put("role",role);
        }else{
            role="customer";
        }

        return Jwts.builder().
                subject(email).
                issuedAt(now).
                expiration(expiry).
                addClaims(claims).
                signWith(secretKey).
                compact();
    }

    public Claims extractClaims(String token)
    {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }
    
    public String extractEmail(String token)
    {
        return extractClaims(token).getSubject();
    }


}
