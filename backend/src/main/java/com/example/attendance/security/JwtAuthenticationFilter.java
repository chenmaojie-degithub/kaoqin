package com.example.attendance.security;
import jakarta.servlet.*; import jakarta.servlet.http.*; import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@Component @RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtService jwtService; private final CustomUserDetailsService users;
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String h=req.getHeader("Authorization");
  if(h!=null&&h.startsWith("Bearer ")&&SecurityContextHolder.getContext().getAuthentication()==null){
   try{ LoginUser u=(LoginUser)users.loadUserByUsername(jwtService.username(h.substring(7))); if(u.isEnabled()) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities())); }catch(Exception ignored){}
  }
  chain.doFilter(req,res);
 }
}

