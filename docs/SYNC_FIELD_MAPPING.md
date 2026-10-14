# 三端字段映射模板

各开发者填写 `sync-field-mapping-template.csv` 中B/C两列，状态/时区转换也须确认。下表是我方实际适配规则，不能要求另外两个数据库使用相同表名或主键。

| 实体/JSON字段 | 我方本地来源/目标 | 规则 |
| --- | --- | --- |
| CLASS.classCode | class_info.class_code | 学校统一代码；映射至本地class_info.id，ID不出库 |
| CLASS.name | class_info.name | 出站展示；入站不覆盖本地名称 |
| COURSE.courseCode | course.course_code | 学校统一代码；映射至本地course.id |
| COURSE.name | course.name | 出站展示；入站不覆盖 |
| STUDENT.studentNo | student.student_no | 保留前导零；映射至本地student.id，不用姓名/登录用户名匹配 |
| STUDENT.name | student.name | 最小展示信息；不覆盖本地档案，不包含电话/邮箱/密码 |
| STUDENT.classCode | student.class_id → class_info.class_code | 必须与本地学生实际班级一致；不自动转班 |
| TASK.entityId / RECORD.taskUuid | sync_identity_map.global_id ↔ attendance_task.id | 来源随机任务UUID，映射后永久稳定 |
| TASK.courseCode/classCode/teacherNo | course_class及course/class_info/teacher的业务代码 | 代码+学年+学期定位本地教学班；不能传course_class_id/teacher_id |
| TASK.academicYear/semester | course_class.academic_year/semester | 三方共同约定学期格式，不靠字符串猜测 |
| TASK.attendanceDate | attendance_task.attendance_date | Asia/Shanghai日期 |
| TASK.status/closedAt | attendance_task.status/closed_at | OPEN/null或CLOSED/带时区时间；任务元数据由owner节点维护 |
| RECORD.entityId | taskUuid+studentUuid → 确定性全局记录UUID | 不是attendance_record.id；对应本地task_id:student_id复合映射 |
| RECORD.studentNo | student.student_no → attendance_record.student_id | 缺本地学生/映射时等待，禁止自动创建用户 |
| RECORD.status | attendance_record.status | NORMAL、ABSENT、LATE、EARLY_LEAVE、LEAVE；仅准确基线更新同一映射记录 |
| RECORD.recordedAt | 出站优先本地check_in_time，否则记录updated_at；无行取当前结果生成时间 | 入站保留在签名sync_event.event_json.payload内，不当作本地check_in_time |
| RECORD.remark | attendance_record.remark | 入站更新普通备注；本地密码产生的结果出站使用通用“考勤结果”备注 |
| RECORD.baseEventId | sync_identity_map.last_event_id | 新记录null；修改引用实际已应用事件ID |
| Event.version | sync_identity_map.version | 新记录1；修正必须当前+1，禁止各节点对同一实体独立重置版本 |
| Event.originNodeId | 本次记录变更的来源节点 | 转发保留；不同节点可顺序修正同一记录 |

我方记录导入只修改普通status/remark，绝不根据协议生成session_id/check_in_time，也不删除已有本地密码签到证据。NORMAL是普通考勤结果；“已在本机通过密码签到”继续需要本机原有会话和时间凭证。

我方旧手工NORMAL可能没有实际attendance_record行，学生查询会合成默认正常。这种本地兼容行为保持；适配器仍可导出明确的NORMAL事件，其他节点可存显式正常记录。因此验收应比对统一业务结果和记录UUID，不能要求各库原始行数永远完全相同。

学生/班级/课程在本轮是身份和参考快照。接收方若已有同代码本地数据，则绑定；缺少时保留WAITING_DEPENDENCY。不会凭外部姓名建账号、建班、建课程或覆盖本地名称。学校名单导入和课程分配继续通过各系统原有管理功能完成。

## 状态映射确认表

| 统一值 | 含义 | 我方值 | B系统本地值（填写） | C系统本地值（填写） |
| --- | --- | --- | --- | --- |
| NORMAL | 正常 | NORMAL |  |  |
| ABSENT | 缺勤 | ABSENT |  |  |
| LATE | 迟到 | LATE |  |  |
| EARLY_LEAVE | 早退 | EARLY_LEAVE |  |  |
| LEAVE | 请假 | LEAVE |  |  |

未知状态禁止自动映射成NORMAL；一个系统没有某状态时应保留原事件待处理/冲突，不能丢弃。任务OPEN/CLOSED是任务编辑/结束状态，不表示密码会话状态。

## 对接验收顺序

先确认学校代码、学号、班级/课程/教师代码和时区→发送参考映射→发送任务→普通记录→另一端读取基线后修正→并发修正冲突→重复/断网/缺映射。对方不需要实现任何密码字段、会话表或远程验证接口。
