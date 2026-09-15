package com.example.attendance.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.*; import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("leave_request")
public class LeaveRequest extends BaseEntity { @TableId(type=IdType.AUTO) private Long id; private Long studentId; private Long courseId; private LocalDateTime startTime; private LocalDateTime endTime; private String reason; private String status; private Long approverId; private String approvalComment; private LocalDateTime approvedAt; }

