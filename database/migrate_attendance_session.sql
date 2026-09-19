USE student_attendance;

CREATE TABLE attendance_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  sign_code CHAR(6) NOT NULL,
  expires_at DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED/EXPIRED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_session_task_status (task_id, status),
  KEY idx_session_expires (status, expires_at),
  CONSTRAINT fk_session_task FOREIGN KEY (task_id) REFERENCES attendance_task (id) ON DELETE RESTRICT,
  CONSTRAINT fk_session_course FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE RESTRICT,
  CONSTRAINT fk_session_class FOREIGN KEY (class_id) REFERENCES class_info (id) ON DELETE RESTRICT,
  CONSTRAINT fk_session_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id) ON DELETE RESTRICT,
  CONSTRAINT chk_session_status CHECK (status IN ('OPEN','CLOSED','EXPIRED')),
  CONSTRAINT chk_session_code CHECK (sign_code REGEXP '^[0-9]{6}$')
) ENGINE=InnoDB COMMENT='60秒密码签到会话';

ALTER TABLE attendance_record
  ADD COLUMN session_id BIGINT NULL AFTER task_id,
  ADD COLUMN check_in_time DATETIME NULL AFTER status,
  ADD UNIQUE KEY uk_record_session_student (session_id, student_id),
  ADD CONSTRAINT fk_record_session FOREIGN KEY (session_id) REFERENCES attendance_session (id) ON DELETE RESTRICT,
  DROP CHECK chk_record_status,
  ADD CONSTRAINT chk_record_status CHECK (status IN ('NORMAL','LATE','EARLY_LEAVE','LEAVE','ABSENT'));
