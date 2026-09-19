package com.example.attendance.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.*; import java.time.LocalDate; import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("attendance_record")
public class AttendanceRecord extends BaseEntity { @TableId(type=IdType.AUTO) private Long id; private Long taskId; private Long sessionId; private Long studentId; private Long courseId; private LocalDate attendanceDate; private String status; private LocalDateTime checkInTime; private String remark; private Long recordedBy; }
