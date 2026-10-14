package com.example.attendance.controller;

import com.example.attendance.config.SecurityConfig;
import com.example.attendance.entity.User;
import com.example.attendance.mapper.*;
import com.example.attendance.security.*;
import com.example.attendance.service.AttendanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;
import com.example.attendance.exception.GlobalExceptionHandler;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AttendanceController.class, properties = "spring.config.import=optional:classpath:/test-no-local-config.properties")
@Import(SecurityConfig.class)
@ContextConfiguration(classes = {AttendanceController.class, SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class AttendancePermissionTest {
    @Autowired MockMvc mvc;
    @MockBean AttendanceService service;
    @MockBean AttendanceTaskMapper tasks;
    @MockBean CourseClassMapper assignments;
    @MockBean TeacherMapper teachers;
    @MockBean StudentMapper students;
    @MockBean JwtService jwt;
    @MockBean CustomUserDetailsService users;

    private UsernamePasswordAuthenticationToken principal(String role) {
        User user = new User(); user.setId(1L); user.setUsername("test"); user.setRole(role); user.setStatus(1);
        LoginUser login = new LoginUser(user); return new UsernamePasswordAuthenticationToken(login,null,login.getAuthorities());
    }
    @Test void teacherCreatePassesRoleGateWhileStudentGets403() throws Exception {
        String body = "{\"courseClassId\":7,\"attendanceDate\":\"2026-10-14\"}";
        mvc.perform(post("/attendance/tasks").with(authentication(principal("TEACHER"))).contentType("application/json").content(body))
                .andExpect(status().isOk());
        verify(service).createTask(any());
        mvc.perform(post("/attendance/tasks").with(authentication(principal("STUDENT"))).contentType("application/json").content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40300));
    }
    @Test void studentCannotOpenOrCloseSessionAndTeacherCannotCheckIn() throws Exception {
        mvc.perform(post("/attendance/sessions").with(authentication(principal("STUDENT"))).contentType("application/json").content("{\"taskId\":4}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/attendance/sessions/5/close").with(authentication(principal("STUDENT")))).andExpect(status().isForbidden());
        mvc.perform(post("/attendance/check-in").with(authentication(principal("TEACHER"))).contentType("application/json").content("{\"sessionId\":5,\"signCode\":\"123456\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void unexpectedLanOriginIsRejectedRatherThanWeakeningCors() throws Exception {
        mvc.perform(options("/attendance/tasks").header("Origin","http://192.168.1.50:5173").header("Access-Control-Request-Method","POST"))
                .andExpect(status().isForbidden());
    }
}
