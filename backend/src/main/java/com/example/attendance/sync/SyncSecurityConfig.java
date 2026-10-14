package com.example.attendance.sync;

import com.example.attendance.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Configuration @RequiredArgsConstructor
public class SyncSecurityConfig {
    private final SyncProperties config;
    private final ObjectMapper json;
    @Bean @Order(1)
    SecurityFilterChain nodeChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/sync/push","/sync/pull")
                .csrf(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c->c.anyRequest().hasAuthority("SYNC_NODE"))
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                        if(!config.isEnabled()) { fail(res,503,"同步未启用");return; }
                        String node=req.getHeader("X-Sync-Node"),auth=req.getHeader("Authorization");
                        boolean valid=false;
                        if(node!=null && auth!=null && auth.startsWith("Sync ")) try {
                            valid=MessageDigest.isEqual(config.peer(node).token().getBytes(StandardCharsets.UTF_8),auth.substring(5).getBytes(StandardCharsets.UTF_8));
                        } catch(IllegalArgumentException ignored) {}
                        if(!valid) { fail(res,401,"节点认证失败");return; }
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(node,null,List.of(new SimpleGrantedAuthority("SYNC_NODE"))));
                        if("POST".equals(req.getMethod())) {
                            byte[] body=req.getInputStream().readNBytes(1_048_577);
                            if(body.length>1_048_576) { fail(res,413,"同步批次过大");return; }
                            var cached=new HttpServletRequestWrapper(req) {
                                @Override public ServletInputStream getInputStream() {
                                    var input=new ByteArrayInputStream(body);
                                    return new ServletInputStream() {
                                        public int read() { return input.read(); }
                                        public boolean isFinished() { return input.available()==0; }
                                        public boolean isReady() { return true; }
                                        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                                    };
                                }
                                @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
                            };
                            chain.doFilter(cached,res);
                        } else chain.doFilter(req,res);
                    }
                }, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private void fail(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(),ApiResponse.error(status*100,message));
    }
}
