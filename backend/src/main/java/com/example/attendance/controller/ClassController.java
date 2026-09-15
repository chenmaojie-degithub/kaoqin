package com.example.attendance.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.attendance.common.ApiResponse;
import com.example.attendance.entity.ClassInfo;
import com.example.attendance.exception.BusinessException;
import com.example.attendance.mapper.ClassInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/classes") @RequiredArgsConstructor
public class ClassController {
    private final ClassInfoMapper mapper;
    @GetMapping public ApiResponse<List<ClassInfo>> list(@RequestParam(required=false)String keyword){return ApiResponse.ok(mapper.selectList(Wrappers.<ClassInfo>lambdaQuery().and(keyword!=null&&!keyword.isBlank(),q->q.like(ClassInfo::getName,keyword).or().like(ClassInfo::getClassCode,keyword)).orderByDesc(ClassInfo::getId)));}
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ApiResponse<ClassInfo> create(@jakarta.validation.Valid @RequestBody ClassInfo value){mapper.insert(value);return ApiResponse.ok(value);}
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<ClassInfo> update(@PathVariable Long id,@jakarta.validation.Valid @RequestBody ClassInfo value){if(mapper.selectById(id)==null)throw BusinessException.notFound("Class not found");value.setId(id);mapper.updateById(value);return ApiResponse.ok(value);}
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<Void> delete(@PathVariable Long id){if(mapper.deleteById(id)==0)throw BusinessException.notFound("Class not found");return ApiResponse.ok();}
}
