# 第一阶段：签到入口与状态修复

日期：2026-10-14。分支：`fix/student-checkin-phase1`。未Push，未修改main；原有未跟踪GPT_PROJECT_HANDOFF.md保留，不作为本次提交内容。

## 实际检查

- 基线提交b9eb281。Vue3/Vite前端、Spring Boot 3.3.5/Java17后端，MyBatis-Plus及JWT角色权限保持。
- 本机MySQL 8.0.30，11张业务表；实际签到表、记录字段与当前schema.sql一致。数据库检查仅执行SELECT，没有创建/迁移/写入/删除真实数据。
- 教师账号与teacher关联均存在；历史403请求的JWT、Origin、实际响应体未保留，无法唯一确定当时原因。
- 当前数据库主键、外键和本地唯一键不能直接作为三节点统一标识，后续详见SYNC_DESIGN.md。

## 根因与修复

| 问题 | 根因 | 最小修复 |
| --- | --- | --- |
| 没有当天课表找不到签到 | today-schedule只枚举当天有效course_schedule | 新增学生只读today-check-ins，按本人班级当天考勤任务返回，独立于课表/学期课表状态 |
| 首页没有入口 | 首页只展示状态 | 新增“去签到”按钮至现有今日安排页顶部签到区 |
| 正常误认为签到完成 | 默认NORMAL被文案显示成已正常签到 | 保留考勤状态，另加todayCheckInState/checkInState；有sessionId和checkInTime才算有签到凭证 |
| 学生错过后来开的码 | 页面仅首次加载 | 今日签到区和首页每5秒刷新，支持手动刷新，卸载清理计时器，防止请求重叠 |
| 过期/关闭看不到明确状态 | studentSession只查OPEN且过期返回null | 返回最新会话的有效状态与签到凭证；过期状态按服务器时间计算，GET不写库 |
| 多任务只签一个首页也显示已签到 | 任意已签到任务就判定成功 | 未完成任务优先，全部任务都有凭证才显示全部已签到 |
| 重复签到/关闭竞争 | 旧异常记录走UPDATE时数据库唯一键不能阻止第二次成功；关闭与签到独立读写 | 复用任务行锁，签到与关闭采用READ_COMMITTED，锁后重新读取会话/记录，手工修改也使用同一任务锁 |
| 前端身份可能滞后 | 菜单依赖持久化user，未调用restore | 首次路由按当前token调用auth/me验证角色；其他标签切换账号时重载；后端角色拒绝记录脱敏日志 |

仍使用60秒服务器有效期、原角色和班级授权，不通过开放权限修复403。未签到不会自动变成缺勤；正常手工考勤与历史默认正常、20分钟修改窗口继续保留。

## 接口

`GET /api/student-portal/today-check-ins`：仅STUDENT，经JWT找本人所在班级，只列今天考勤任务。

返回保留的考勤status，并包含taskId、courseName、checkedIn、checkInTime、checkInState、sessionStatus、sessionId（如有）、expiresAt（如有）、serverTime。无signCode。

checkInState：WAITING（未开码）、OPEN（待签到）、SIGNED（有成功凭证）、EXPIRED（码已过期）、CLOSED（提前关闭/任务停止）。有签到凭证时不因之后过期/重开而变为未签到。

## 403排查结论

自动测试确认教师通过角色门槛后，仍必须属于目标教学班；学生创建/关闭、教师以学生接口签到均被拒绝。未配置局域网Origin的预检请求返回403，当前默认只允许localhost/127.0.0.1来源，尚未进入学校跨域配置阶段。

以上是已复现的拒绝路径，不代表历史截图必然由其中某一个原因引起。下次发生时核对Network中的请求路径、响应体、Origin、auth/me的实际角色，以及教学班teacher_id；不要输出JWT。角色门槛拒绝日志仅记录方法/路径/权限列表，教学班归属拒绝仍返回明确业务消息。

## 验证与限制

- Maven测试：17项全部通过；含服务状态/重复/归属测试、无课表学生入口测试、Dashboard多任务测试和真实Spring Security过滤链的MockMvc角色/跨域测试。
- 前端生产构建通过；存在原有大体积chunk提示。
- 上述后端测试的数据库Mapper为Mock，不连接真实业务库；不是三节点或真实MySQL并发测试。
- 未验证：真实浏览器完整操作链、两个真实教师账号的历史403复现、MySQL并发负载、学校局域网跨域及三机互通。
- 不需要新增数据库字段或执行迁移；本阶段没有SQL结构变更。
- 当前已有业务数据未被导出或用于新测试夹具。

## 改动范围

- 后端：StudentPortalController、AttendanceServiceImpl、GlobalExceptionHandler。
- 前端：TodayScheduleView、StudentDashboardView、MyAttendanceView、AttendanceDetailDrawer、auth store、router、main、request。
- 测试：AttendanceServiceImplTest、StudentPortalControllerTest、AttendancePermissionTest。
- 文档：README、PHASE1_REPORT、SYNC_DESIGN；忽略规则补充本地环境文件及导出/备份目录。

第二阶段仅提供设计，尚未创建同步接口、辅助表、自动任务或三个数据库。需先共同确认协议和异构系统适配规则。
