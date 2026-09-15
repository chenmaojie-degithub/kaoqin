package com.example.attendance.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.*; import java.time.LocalTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("course_schedule")
public class CourseSchedule extends BaseEntity { @TableId(type=IdType.AUTO) private Long id; private Long courseClassId; private Integer weekday; private LocalTime startTime; private LocalTime endTime; private String location; private Integer startWeek; private Integer endWeek; }
