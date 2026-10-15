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

@WebMvcTest(value = AttendanceController.class, properties = {"spring.config.import=optional:classpath:/test-no-local-config.properties", "attendance.cors.allowed-origins=http://10.32.54.192:5173"})
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

    @Test void schoolOriginAllowsPreflightButStillRequiresJwtAndTeacherRole() throws Exception {
        String origin="http://10.32.54.192:5173";
            mvc.perform(options("/attendance/tasks").header("Origin",origin).header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type"))
                    .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin",origin));
            mvc.perform(get("/attendance/tasks").header("Origin",origin)).andExpect(status().isUnauthorized());
            mvc.perform(post("/attendance/tasks").header("Origin",origin).with(authentication(principal("STUDENT"))).contentType("application/json").content("{\"courseClassId\":7,\"attendanceDate\":\"2026-10-15\"}"))
                    .andExpect(status().isForbidden());
        for (String rejectedOrigin : new String[]{"http://10.32.54.192:9999", "http://10.32.54.193:5173", "http://10.32.54.172:5173"}) {
        mvc.perform(options("/attendance/tasks").header("Origin",rejectedOrigin).header("Access-Control-Request-Method","POST"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void bearerRequestUsesCurrentAccountRoleAndRejectsDisabledAccount() throws Exception {
        when(jwt.username("test-token")).thenReturn("test");
        LoginUser teacher = (LoginUser) principal("TEACHER").getPrincipal();
        when(users.loadUserByUsername("test")).thenReturn(teacher);
        String body = "{\"courseClassId\":7,\"attendanceDate\":\"2026-10-14\"}";
        mvc.perform(post("/attendance/tasks").header("Authorization", "Bearer test-token").contentType("application/json").content(body))
                .andExpect(status().isOk());
        verify(service).createTask(any()); clearInvocations(service);
        when(users.loadUserByUsername("test")).thenReturn((LoginUser) principal("STUDENT").getPrincipal());
        mvc.perform(post("/attendance/tasks").header("Authorization", "Bearer test-token").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        teacher.user().setStatus(0); when(users.loadUserByUsername("test")).thenReturn(teacher);
        mvc.perform(post("/attendance/tasks").header("Authorization", "Bearer test-token").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
