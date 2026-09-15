package com.example.attendance.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("`user`")
public class User extends BaseEntity {
 @TableId(type=IdType.AUTO) private Long id; private String username; @com.fasterxml.jackson.annotation.JsonIgnore private String password; private String realName; private String role; private Integer status; private LocalDateTime lastLoginAt;
}
