package com.example.attendance.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("course_class")
public class CourseClass extends BaseEntity { @TableId(type=IdType.AUTO) private Long id; private Long courseId; private Long classId; private Long teacherId; private String academicYear; private String semester; }

