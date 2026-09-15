package com.example.attendance.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Date;
@Service
public class JwtService {
 private final SecretKey key; private final long expirationMinutes;
 public JwtService(@Value("${attendance.jwt.secret}") String secret,@Value("${attendance.jwt.expiration-minutes}") long minutes){ this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.expirationMinutes=minutes; }
 public String create(LoginUser user){ Instant now=Instant.now(); return Jwts.builder().subject(user.getUsername()).claim("uid",user.id()).claim("role",user.role()).issuedAt(Date.from(now)).expiration(Date.from(now.plus(Duration.ofMinutes(expirationMinutes)))).signWith(key).compact(); }
 public String username(String token){ return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
 public long expiresInSeconds(){ return expirationMinutes*60; }
}

