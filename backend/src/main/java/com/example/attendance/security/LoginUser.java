package com.example.attendance.security;
import com.example.attendance.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
public record LoginUser(User user) implements UserDetails {
 public Long id(){ return user.getId(); }
 public String role(){ return user.getRole(); }
 @Override public Collection<SimpleGrantedAuthority> getAuthorities(){ return List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole())); }
 @Override public String getPassword(){ return user.getPassword(); }
 @Override public String getUsername(){ return user.getUsername(); }
 @Override public boolean isEnabled(){ return Integer.valueOf(1).equals(user.getStatus()); }
}

