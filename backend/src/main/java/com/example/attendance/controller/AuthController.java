package com.example.attendance.controller;

import com.example.attendance.common.ApiResponse;
import com.example.attendance.dto.LoginRequest;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.security.JwtService;
import com.example.attendance.security.LoginUser;
import com.example.attendance.security.SecurityUtils;
import com.example.attendance.vo.LoginVO;
import com.example.attendance.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginUser user = (LoginUser) authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())).getPrincipal();
            String token = jwtService.create(user);
            return ApiResponse.ok(new LoginVO(token, "Bearer", jwtService.expiresInSeconds(), UserVO.from(user.user())));
        } catch (AuthenticationException ex) {
            throw new BusinessException(40101, "Invalid username, password, or disabled account");
        }
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me() { return ApiResponse.ok(UserVO.from(SecurityUtils.current().user())); }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() { return ApiResponse.ok(); }
}

