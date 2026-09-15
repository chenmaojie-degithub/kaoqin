package com.example.attendance.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.*; import java.math.BigDecimal;
@Data @EqualsAndHashCode(callSuper=true) @TableName("course")
public class Course extends BaseEntity { @TableId(type=IdType.AUTO) private Long id; @jakarta.validation.constraints.NotBlank private String courseCode; @jakarta.validation.constraints.NotBlank private String name; private BigDecimal credit; private Integer totalHours; private String description; private Integer status; }
