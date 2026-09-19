package com.example.attendance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @EqualsAndHashCode(callSuper = true) @TableName("attendance_session")
public class AttendanceSession extends BaseEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private Long courseId;
    private Long classId;
    private Long teacherId;
    private String signCode;
    private LocalDateTime expiresAt;
    private String status;
}
