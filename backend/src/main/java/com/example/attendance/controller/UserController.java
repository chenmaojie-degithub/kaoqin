package com.example.attendance.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.attendance.common.*;
import com.example.attendance.dto.UserSaveRequest;
import com.example.attendance.entity.User;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/users") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class UserController {
    private final UserMapper mapper; private final PasswordEncoder encoder;
    @GetMapping public ApiResponse<PageResult<User>> page(@RequestParam(defaultValue="1")long page,@RequestParam(defaultValue="10")long size,@RequestParam(required=false)String keyword,@RequestParam(required=false)String role){return ApiResponse.ok(PageResult.of(mapper.selectPage(new Page<>(page,size),Wrappers.<User>lambdaQuery().and(keyword!=null&&!keyword.isBlank(),q->q.like(User::getUsername,keyword).or().like(User::getRealName,keyword)).eq(role!=null&&!role.isBlank(),User::getRole,role).orderByDesc(User::getId))));}
    @PostMapping public ApiResponse<User> create(@Valid @RequestBody UserSaveRequest r){if(r.password()==null||r.password().isBlank())throw new BusinessException(40000,"Password is required");User u=new User();fill(u,r);u.setPassword(encoder.encode(r.password()));mapper.insert(u);return ApiResponse.ok(u);}
    @PutMapping("/{id}") public ApiResponse<User> update(@PathVariable Long id,@Valid @RequestBody UserSaveRequest r){User u=mapper.selectById(id);if(u==null)throw BusinessException.notFound("User not found");fill(u,r);if(r.password()!=null&&!r.password().isBlank())u.setPassword(encoder.encode(r.password()));mapper.updateById(u);return ApiResponse.ok(u);}
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id){if(mapper.deleteById(id)==0)throw BusinessException.notFound("User not found");return ApiResponse.ok();}
    private void fill(User u,UserSaveRequest r){u.setUsername(r.username());u.setRealName(r.realName());u.setRole(r.role());u.setStatus(r.status()==null?1:r.status());}
}

