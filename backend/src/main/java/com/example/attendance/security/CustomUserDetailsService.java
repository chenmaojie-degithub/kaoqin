package com.example.attendance.security;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.attendance.entity.User;
import com.example.attendance.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
 private final UserMapper userMapper;
 @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
  User user=userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,username));
  if(user==null) throw new UsernameNotFoundException("用户名或密码错误");
  return new LoginUser(user);
 }
}

