package com.example.attendance.vo;
import com.example.attendance.entity.User;
public record UserVO(Long id, String username, String realName, String role) {
 public static UserVO from(User u) { return new UserVO(u.getId(),u.getUsername(),u.getRealName(),u.getRole()); }
}

