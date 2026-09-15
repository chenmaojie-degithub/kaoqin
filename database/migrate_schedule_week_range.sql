USE student_attendance;

-- 当前项目的 2026-2027 第一学期共 20 周。旧课表按全学期有效回填。
-- 如果部署环境的当前学期总周数不同，请先修改此变量再执行。
SET @schedule_default_end_week = 20;

ALTER TABLE course_schedule
  ADD COLUMN start_week SMALLINT NULL AFTER location,
  ADD COLUMN end_week SMALLINT NULL AFTER start_week;

UPDATE course_schedule
SET start_week = 1,
    end_week = @schedule_default_end_week
WHERE start_week IS NULL OR end_week IS NULL;

ALTER TABLE course_schedule
  MODIFY COLUMN start_week SMALLINT NOT NULL DEFAULT 1,
  MODIFY COLUMN end_week SMALLINT NOT NULL DEFAULT 20,
  ADD KEY idx_schedule_course_class (course_class_id),
  DROP INDEX uk_schedule,
  ADD UNIQUE KEY uk_schedule (course_class_id, weekday, start_time, start_week, end_week),
  ADD CONSTRAINT chk_schedule_week_range CHECK (start_week >= 1 AND end_week >= start_week);
