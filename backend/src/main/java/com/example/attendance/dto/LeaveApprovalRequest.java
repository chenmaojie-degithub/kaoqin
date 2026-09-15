package com.example.attendance.dto;
import jakarta.validation.constraints.*;
public record LeaveApprovalRequest(@Pattern(regexp="APPROVED|REJECTED") String status,@Size(max=500) String comment){}

