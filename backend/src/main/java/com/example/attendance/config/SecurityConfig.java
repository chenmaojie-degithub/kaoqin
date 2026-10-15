package com.example.attendance.config;
import com.example.attendance.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.nio.charset.StandardCharsets; import java.util.Arrays; import java.util.List; import java.util.Map;
@Configuration @EnableMethodSecurity @RequiredArgsConstructor
public class SecurityConfig {
 private final JwtAuthenticationFilter jwtFilter; private final ObjectMapper mapper;
 @Value("${attendance.cors.allowed-origins:}") private String allowedOrigins;
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
 @Bean CorsConfigurationSource cors(){ CorsConfiguration c=new CorsConfiguration(); c.setAllowedOriginPatterns(List.of("http://localhost:*","http://127.0.0.1:*")); c.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s->!s.isEmpty()).peek(s->{if(s.contains("*") || !s.matches("https?://[^/\\s?#]+")) throw new IllegalArgumentException("CORS origins must be explicit HTTP(S) origins");}).toList()); c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS")); c.setAllowedHeaders(List.of("Authorization","Content-Type","Accept")); c.setAllowCredentials(true); UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/**",c); return s; }
 @Bean SecurityFilterChain chain(HttpSecurity h)throws Exception{
  h.csrf(x->x.disable()).cors(x->x.configurationSource(cors())).sessionManagement(x->x.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(x->x.requestMatchers("/auth/login","/swagger-ui/**","/v3/api-docs/**").permitAll().anyRequest().authenticated())
   .exceptionHandling(x->x.authenticationEntryPoint((q,r,e)->json(r,401,40100,"登录状态无效或已过期")).accessDeniedHandler((q,r,e)->json(r,403,40300,"无权访问该资源")))
   .addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter.class);
  return h.build();
 }
 private void json(HttpServletResponse r,int status,int code,String msg)throws java.io.IOException{r.setStatus(status);r.setCharacterEncoding(StandardCharsets.UTF_8.name());r.setContentType(MediaType.APPLICATION_JSON_VALUE);mapper.writeValue(r.getWriter(),Map.of("code",code,"message",msg));}
}

