package com.example.attendance.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("teacher")
public class Teacher extends BaseEntity { @TableId(type=IdType.AUTO) private Long id; private Long userId; private String teacherNo; private String name; private String gender; private String title; private String phone; private String email; private Integer status; }

