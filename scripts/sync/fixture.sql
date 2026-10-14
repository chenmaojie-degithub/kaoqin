-- Fictional accounts only. Password for these test accounts: SyncTest!2026.
-- Never import this fixture into a production database.
INSERT INTO `user`(username,password,real_name,role) VALUES
('sync_admin','$2a$10$334x0/YFom.e1D3WkEZoWenqx5I60N1edeY.uqO2WXWxhVoKI2TuW','Fictional Admin','ADMIN'),
('sync_teacher','$2a$10$334x0/YFom.e1D3WkEZoWenqx5I60N1edeY.uqO2WXWxhVoKI2TuW','Fictional Teacher','TEACHER'),
('sync_student','$2a$10$334x0/YFom.e1D3WkEZoWenqx5I60N1edeY.uqO2WXWxhVoKI2TuW','Fictional Student','STUDENT');
INSERT INTO teacher(user_id,teacher_no,name) SELECT id,'T001','Fictional Teacher' FROM `user` WHERE username='sync_teacher';
INSERT INTO class_info(class_code,name,grade) VALUES('CLASS_1','Fictional Class','2026');
INSERT INTO student(user_id,student_no,name,class_id) SELECT u.id,'S001','Fictional Student',c.id FROM `user` u CROSS JOIN class_info c WHERE u.username='sync_student';
INSERT INTO course(course_code,name) VALUES('COURSE_A','Fictional Course A'),('COURSE_B','Fictional Course B'),('COURSE_C','Fictional Course C');
INSERT INTO course_class(course_id,class_id,teacher_id,academic_year,semester) SELECT c.id,ci.id,t.id,'2026-2027','第一学期' FROM course c CROSS JOIN class_info ci CROSS JOIN teacher t;
