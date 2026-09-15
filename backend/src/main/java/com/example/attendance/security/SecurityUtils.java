package com.example.attendance.security;
import com.example.attendance.exception.BusinessException;
import org.springframework.security.core.context.SecurityContextHolder;
public final class SecurityUtils {
 private SecurityUtils(){}
 public static LoginUser current(){ Object p=SecurityContextHolder.getContext().getAuthentication().getPrincipal(); if(p instanceof LoginUser u)return u; throw new BusinessException(40100,"请先登录"); }
 public static boolean isAdmin(){ return "ADMIN".equals(current().role()); }
}

