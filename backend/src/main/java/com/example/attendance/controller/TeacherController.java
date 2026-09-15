package com.example.attendance.controller;

import com.example.attendance.common.*;
import com.example.attendance.dto.TeacherSaveRequest;
import com.example.attendance.entity.Teacher;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.mapper.TeacherMapper;
import com.example.attendance.service.TeacherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/teachers") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class TeacherController {
    private final TeacherService service;
    private final TeacherMapper mapper;
    @GetMapping public ApiResponse<PageResult<Teacher>> page(@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="10") long size,@RequestParam(required=false) String keyword){return ApiResponse.ok(PageResult.of(service.page(page,size,keyword)));}
    @GetMapping("/{id}") public ApiResponse<Teacher> get(@PathVariable Long id){Teacher value=mapper.selectById(id);if(value==null)throw BusinessException.notFound("Teacher not found");return ApiResponse.ok(value);}
    @PostMapping public ApiResponse<Teacher> create(@Valid @RequestBody TeacherSaveRequest request){return ApiResponse.ok(service.create(request));}
    @PutMapping("/{id}") public ApiResponse<Teacher> update(@PathVariable Long id,@Valid @RequestBody TeacherSaveRequest request){return ApiResponse.ok(service.update(id,request));}
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok();}
}
