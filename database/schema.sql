CREATE DATABASE IF NOT EXISTS student_attendance
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE student_attendance;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS leave_request;
DROP TABLE IF EXISTS attendance_record;
DROP TABLE IF EXISTS attendance_task;
DROP TABLE IF EXISTS course_schedule;
DROP TABLE IF EXISTS course_class;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS class_info;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS teacher;
DROP TABLE IF EXISTS `user`;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(100) NOT NULL,
  real_name VARCHAR(50) NOT NULL,
  role VARCHAR(20) NOT NULL COMMENT 'ADMIN/TEACHER/STUDENT',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0 disabled, 1 enabled',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_role_status (role, status),
  CONSTRAINT chk_user_role CHECK (role IN ('ADMIN','TEACHER','STUDENT'))
) ENGINE=InnoDB COMMENT='用户账号';

CREATE TABLE teacher (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  teacher_no VARCHAR(30) NOT NULL,
  name VARCHAR(50) NOT NULL,
  gender VARCHAR(10) NULL,
  title VARCHAR(50) NULL,
  phone VARCHAR(20) NULL,
  email VARCHAR(100) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_teacher_user (user_id),
  UNIQUE KEY uk_teacher_no (teacher_no),
  KEY idx_teacher_name (name),
  CONSTRAINT fk_teacher_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='教师';

CREATE TABLE course (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_code VARCHAR(30) NOT NULL,
  name VARCHAR(100) NOT NULL,
  credit DECIMAL(4,1) NOT NULL DEFAULT 0,
  total_hours INT NOT NULL DEFAULT 0,
  description VARCHAR(500) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_course_code (course_code),
  KEY idx_course_name (name)
) ENGINE=InnoDB COMMENT='课程';

CREATE TABLE class_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  class_code VARCHAR(30) NOT NULL,
  name VARCHAR(100) NOT NULL,
  grade VARCHAR(20) NOT NULL,
  major VARCHAR(100) NULL,
  advisor_teacher_id BIGINT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_class_code (class_code),
  KEY idx_class_grade (grade),
  KEY idx_class_advisor (advisor_teacher_id),
  CONSTRAINT fk_class_advisor FOREIGN KEY (advisor_teacher_id) REFERENCES teacher (id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='班级';

CREATE TABLE student (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  student_no VARCHAR(30) NOT NULL,
  name VARCHAR(50) NOT NULL,
  gender VARCHAR(10) NULL,
  phone VARCHAR(20) NULL,
  email VARCHAR(100) NULL,
  class_id BIGINT NOT NULL,
  enrollment_date DATE NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_student_user (user_id),
  UNIQUE KEY uk_student_no (student_no),
  KEY idx_student_name (name),
  KEY idx_student_class (class_id),
  CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE RESTRICT,
  CONSTRAINT fk_student_class FOREIGN KEY (class_id) REFERENCES class_info (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='学生';

CREATE TABLE course_class (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  academic_year VARCHAR(20) NOT NULL,
  semester VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_course_class_term (course_id, class_id, teacher_id, academic_year, semester),
  KEY idx_cc_teacher (teacher_id),
  KEY idx_cc_class (class_id),
  CONSTRAINT fk_cc_course FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE RESTRICT,
  CONSTRAINT fk_cc_class FOREIGN KEY (class_id) REFERENCES class_info (id) ON DELETE RESTRICT,
  CONSTRAINT fk_cc_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='课程班级教师关联';

CREATE TABLE course_schedule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_class_id BIGINT NOT NULL,
  weekday TINYINT NOT NULL COMMENT '1 Monday - 7 Sunday',
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  location VARCHAR(100) NOT NULL,
  start_week SMALLINT NOT NULL DEFAULT 1,
  end_week SMALLINT NOT NULL DEFAULT 20,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_schedule (course_class_id, weekday, start_time, start_week, end_week),
  KEY idx_schedule_course_class (course_class_id),
  CONSTRAINT fk_schedule_cc FOREIGN KEY (course_class_id) REFERENCES course_class (id) ON DELETE RESTRICT,
  CONSTRAINT chk_schedule_weekday CHECK (weekday BETWEEN 1 AND 7),
  CONSTRAINT chk_schedule_time CHECK (end_time > start_time),
  CONSTRAINT chk_schedule_week_range CHECK (start_week >= 1 AND end_week >= start_week)
) ENGINE=InnoDB COMMENT='课程上课安排';

CREATE TABLE attendance_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_class_id BIGINT NOT NULL,
  attendance_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
  closed_at DATETIME NULL COMMENT '考勤停止时间，停止后20分钟内可修改',
  remark VARCHAR(500) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_task_cc_date (course_class_id, attendance_date),
  KEY idx_task_date (attendance_date),
  CONSTRAINT fk_task_cc FOREIGN KEY (course_class_id) REFERENCES course_class (id) ON DELETE RESTRICT,
  CONSTRAINT fk_task_creator FOREIGN KEY (created_by) REFERENCES `user` (id) ON DELETE RESTRICT,
  CONSTRAINT chk_task_status CHECK (status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB COMMENT='考勤任务';

CREATE TABLE attendance_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  attendance_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT '仅保存异常：LATE/EARLY_LEAVE/LEAVE/ABSENT；NORMAL由任务名单推导',
  remark VARCHAR(500) NULL,
  recorded_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_record_student_course_date (student_id, course_id, attendance_date),
  KEY idx_record_task (task_id),
  KEY idx_record_student_date (student_id, attendance_date),
  KEY idx_record_course_date (course_id, attendance_date),
  KEY idx_record_status_date (status, attendance_date),
  CONSTRAINT fk_record_task FOREIGN KEY (task_id) REFERENCES attendance_task (id) ON DELETE RESTRICT,
  CONSTRAINT fk_record_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE RESTRICT,
  CONSTRAINT fk_record_course FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE RESTRICT,
  CONSTRAINT fk_record_user FOREIGN KEY (recorded_by) REFERENCES `user` (id) ON DELETE RESTRICT,
  CONSTRAINT chk_record_status CHECK (status IN ('LATE','EARLY_LEAVE','LEAVE','ABSENT'))
) ENGINE=InnoDB COMMENT='考勤记录';

CREATE TABLE leave_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  course_id BIGINT NULL COMMENT 'NULL means all courses in time range',
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  approver_id BIGINT NULL,
  approval_comment VARCHAR(500) NULL,
  approved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_leave_student_status (student_id, status),
  KEY idx_leave_approver_status (approver_id, status),
  KEY idx_leave_time (start_time, end_time),
  CONSTRAINT fk_leave_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE RESTRICT,
  CONSTRAINT fk_leave_course FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE RESTRICT,
  CONSTRAINT fk_leave_approver FOREIGN KEY (approver_id) REFERENCES `user` (id) ON DELETE RESTRICT,
  CONSTRAINT chk_leave_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  CONSTRAINT chk_leave_time CHECK (end_time > start_time)
) ENGINE=InnoDB COMMENT='请假申请';
