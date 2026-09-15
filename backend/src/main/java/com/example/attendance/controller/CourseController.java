package com.example.attendance.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.attendance.common.ApiResponse;
import com.example.attendance.entity.Course;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/courses") @RequiredArgsConstructor
public class CourseController {
    private final CourseMapper mapper;
    @GetMapping public ApiResponse<List<Course>> list(@RequestParam(required=false)String keyword){return ApiResponse.ok(mapper.selectList(Wrappers.<Course>lambdaQuery().and(keyword!=null&&!keyword.isBlank(),q->q.like(Course::getName,keyword).or().like(Course::getCourseCode,keyword)).orderByDesc(Course::getId)));}
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<Course> create(@jakarta.validation.Valid @RequestBody Course value){mapper.insert(value);return ApiResponse.ok(value);}
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<Course> update(@PathVariable Long id,@jakarta.validation.Valid @RequestBody Course value){if(mapper.selectById(id)==null)throw BusinessException.notFound("Course not found");value.setId(id);mapper.updateById(value);return ApiResponse.ok(value);}
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<Void> delete(@PathVariable Long id){if(mapper.deleteById(id)==0)throw BusinessException.notFound("Course not found");return ApiResponse.ok();}
}
