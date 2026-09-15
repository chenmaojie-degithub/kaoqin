package com.example.attendance.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("class_info")
public class ClassInfo extends BaseEntity { @TableId(type=IdType.AUTO) private Long id; @jakarta.validation.constraints.NotBlank private String classCode; @jakarta.validation.constraints.NotBlank private String name; @jakarta.validation.constraints.NotBlank private String grade; private String major; private Long advisorTeacherId; private Integer status; }
