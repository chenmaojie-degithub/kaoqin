-- Fictional demo data only. Import only into a newly initialized demo database.
USE student_attendance;

-- All demo accounts use password: 123456 (BCrypt hash).
INSERT INTO `user` (id, username, password, real_name, role, status) VALUES
(1, 'admin',   '$2a$10$WOSQJNROOOUASR0e0Sbrhu0871Dj8IxEcgxxmQpGWb7KR442A2I0u', '系统管理员', 'ADMIN', 1),
(2, 'teacher', '$2a$10$WOSQJNROOOUASR0e0Sbrhu0871Dj8IxEcgxxmQpGWb7KR442A2I0u', '张老师', 'TEACHER', 1),
(3, 'student', '$2a$10$WOSQJNROOOUASR0e0Sbrhu0871Dj8IxEcgxxmQpGWb7KR442A2I0u', '李同学', 'STUDENT', 1);

INSERT INTO teacher (id, user_id, teacher_no, name, gender, title, phone, email) VALUES
(1, 2, 'T2026001', '张老师', '男', '讲师', '13800000001', 'teacher@example.com');

INSERT INTO class_info (id, class_code, name, grade, major, advisor_teacher_id) VALUES
(1, 'CS2026-01', '计算机科学2026级1班', '2026', '计算机科学与技术', 1);

INSERT INTO student (id, user_id, student_no, name, gender, phone, email, class_id, enrollment_date) VALUES
(1, 3, 'S2026001', '李同学', '女', '13800000002', 'student@example.com', 1, '2026-09-01');

INSERT INTO course (id, course_code, name, credit, total_hours, description) VALUES
(1, 'CS101', 'Java程序设计', 4.0, 64, 'Java语言与面向对象程序设计');

INSERT INTO course_class (id, course_id, class_id, teacher_id, academic_year, semester) VALUES
(1, 1, 1, 1, '2026-2027', '第一学期');

INSERT INTO course_schedule (course_class_id, weekday, start_time, end_time, location, start_week, end_week) VALUES
(1, 3, '08:00:00', '09:50:00', '教学楼A-201', 1, 20);

INSERT INTO attendance_task (id, course_class_id, attendance_date, status, closed_at, remark, created_by) VALUES
(1, 1, '2026-09-09', 'CLOSED', '2026-09-09 10:20:00', '示例考勤', 2);

INSERT INTO attendance_record (task_id, student_id, course_id, attendance_date, status, remark, recorded_by) VALUES
(1, 1, 1, '2026-09-09', 'ABSENT', '示例缺勤', 2);
