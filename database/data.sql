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

-- 2532013班名单；学生登录用户名为学号，初始密码统一为 123456。
INSERT INTO class_info (id, class_code, name, grade, major, advisor_teacher_id) VALUES
(2, '2532013', '2532013班', '2025', NULL, 1);

CREATE TEMPORARY TABLE demo_roster (
  student_no VARCHAR(30) PRIMARY KEY,
  name VARCHAR(50) NOT NULL
);

INSERT INTO demo_roster (student_no, name) VALUES
('253201301', '吕嘉兴'), ('253201302', '杨承瑞'), ('253201303', '刘旭'),
('253201304', '冯载轩'), ('253201305', '王至轩'), ('253201306', '贾澎博'),
('253201307', '张浩然'), ('253201308', '张铠鹏'), ('253201309', '王浩宇'),
('253201310', '闫霄同'), ('253201311', '高政煜'), ('253201312', '肖文宇'),
('253201313', '赵亦寒'), ('253201314', '宋新宇'), ('253201315', '石卓烁'),
('253201316', '王鸿儒'), ('253201317', '张迦恩'), ('253201318', '刘家烨'),
('253201319', '刘镕赫'), ('253201320', '周语轩'), ('253201321', '龙子河'),
('253201322', '陈茂杰'), ('253201323', '刘佳博'), ('253201324', '姬罡'),
('253201325', '陈羽泽'), ('253201326', '王思源'), ('253201327', '邓天成'),
('253201328', '刘敬坤'), ('253201329', '张鑫睿'), ('253201330', '李武斌'),
('253201331', '张天烁'), ('253201332', '芦睿'), ('253201333', '李宗岳'),
('253201334', '王鹏宇'), ('253201335', '康济恩'), ('253201336', '施思年'),
('253201337', '雷俊颖'), ('253201338', '库锦芸'), ('253201339', '高璟怡'),
('253201340', '田嘉慧'), ('253201341', '李晔霞'), ('253201342', '徐嘉嘉');

INSERT INTO `user` (username, password, real_name, role, status)
SELECT student_no, '$2a$10$WOSQJNROOOUASR0e0Sbrhu0871Dj8IxEcgxxmQpGWb7KR442A2I0u', name, 'STUDENT', 1
FROM demo_roster;

INSERT INTO student (user_id, student_no, name, class_id, enrollment_date)
SELECT u.id, r.student_no, r.name, 2, '2025-09-01'
FROM demo_roster r
JOIN `user` u ON u.username = r.student_no;

DROP TEMPORARY TABLE demo_roster;

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
