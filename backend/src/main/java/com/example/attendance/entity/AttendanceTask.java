package com.example.attendance.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.*; import java.time.LocalDate; import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("attendance_task")
public class AttendanceTask extends BaseEntity { @TableId(type=IdType.AUTO) private Long id; private Long courseClassId; private LocalDate attendanceDate; private String status; private LocalDateTime closedAt; private String remark; private Long createdBy; }
