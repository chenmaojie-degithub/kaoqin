package com.example.attendance.controller;

import com.example.attendance.common.ApiResponse; import com.example.attendance.dto.*; import com.example.attendance.entity.LeaveRequest; import com.example.attendance.service.LeaveService; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/leaves") @RequiredArgsConstructor
public class LeaveController {
 private final LeaveService service;
 @GetMapping public ApiResponse<List<LeaveRequest>> list(@RequestParam(required=false)String status){return ApiResponse.ok(service.list(status));}
 @PostMapping @PreAuthorize("hasRole('STUDENT')") public ApiResponse<LeaveRequest> create(@Valid @RequestBody LeaveCreateRequest request){return ApiResponse.ok(service.create(request));}
 @DeleteMapping("/{id}") @PreAuthorize("hasRole('STUDENT')") public ApiResponse<Void> cancel(@PathVariable Long id){service.cancel(id);return ApiResponse.ok();}
 @PutMapping("/{id}/approval") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')") public ApiResponse<LeaveRequest> approve(@PathVariable Long id,@Valid @RequestBody LeaveApprovalRequest request){return ApiResponse.ok(service.approve(id,request));}
}
