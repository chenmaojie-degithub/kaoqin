package com.example.attendance.controller;

import com.example.attendance.common.*;
import com.example.attendance.dto.StudentSaveRequest;
import com.example.attendance.entity.Student;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.mapper.StudentMapper;
import com.example.attendance.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/students") @RequiredArgsConstructor
public class StudentController {
    private final StudentService service;
    private final StudentMapper mapper;
    @GetMapping @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<Student>> page(@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="10") long size,@RequestParam(required=false) String name,@RequestParam(required=false) String studentNo,@RequestParam(required=false) Long classId){return ApiResponse.ok(PageResult.of(service.page(page,size,name,studentNo,classId)));}
    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<Student> get(@PathVariable Long id){Student value=mapper.selectById(id);if(value==null)throw BusinessException.notFound("Student not found");return ApiResponse.ok(value);}
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<Student> create(@Valid @RequestBody StudentSaveRequest request){return ApiResponse.ok(service.create(request));}
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<Student> update(@PathVariable Long id,@Valid @RequestBody StudentSaveRequest request){return ApiResponse.ok(service.update(id,request));}
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok();}
}

