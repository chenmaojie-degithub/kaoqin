package com.example.attendance.service;
import com.example.attendance.dto.*; import com.example.attendance.entity.LeaveRequest; import java.util.List;
public interface LeaveService { LeaveRequest create(LeaveCreateRequest r); List<LeaveRequest> list(String status); LeaveRequest approve(Long id,LeaveApprovalRequest r); void cancel(Long id); }
