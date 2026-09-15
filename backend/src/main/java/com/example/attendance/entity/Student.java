package com.example.attendance.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.*; import java.time.LocalDate;
@Data @EqualsAndHashCode(callSuper=true) @TableName("student")
public class Student extends BaseEntity { @TableId(type=IdType.AUTO) private Long id; private Long userId; private String studentNo; private String name; private String gender; private String phone; private String email; private Long classId; private LocalDate enrollmentDate; private Integer status; }

