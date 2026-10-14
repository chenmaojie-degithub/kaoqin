# 学生考勤管理系统

这是一个可运行的前后端分离学生考勤管理系统，包含管理员、教师和学生三种角色，支持资料管理、课程分配、考勤任务、批量录入、请假审批和真实数据统计。

## 签到入口修复与三节点数据同步

学生首页点击“去签到”，进入“课程 / 今日安排”顶部的“今日签到任务”。该区域按本人班级当天考勤任务展示，不要求当天存在课表。教师在“考勤任务”对今天的任务开启60秒签到后，学生页面每5秒发现新任务，也可以点击“刷新签到”。

“考勤正常”与“已密码签到”分别展示；只有实际成功签到凭证才显示已签到。未开码、待签到、过期、关闭都有独立提示，过期后需要教师重新开启。页面倒计时用于展示，服务器决定有效性。旧手工考勤与20分钟修改规则继续保留。

本次第一阶段无需新增字段或执行迁移SQL，不要重新执行schema.sql覆盖已有库。

2026-10-14复核：补齐详情凭证判断与教师默认日期时区问题；Maven 20项测试及前端构建通过。历史创建403尚未取得原请求证据，真实浏览器与MySQL并发验收仍待完成，详见报告。

- [第一阶段检查、修改和验证报告](docs/PHASE1_REPORT.md)
- [历史架构草稿（已由v2协议替代）](docs/SYNC_DESIGN.md)

现已实现节点认证的 `POST /api/sync/push` 和 `GET /api/sync/pull`、v2普通考勤JSON事件、增量日志、持久化去重/冲突、默认5秒自动Push/Pull，以及管理员“数据同步”页面。同步默认关闭；启用前仅增量执行 `database/migrate_sync.sql` 并配置节点。现有第一阶段无需迁移的说明只适用于签到修复本身。

- [当前v2统一协议与签名规则](docs/SYNC_PROTOCOL.md)
- [五实体JSON样例](docs/sync-v2-examples.json) · [字段映射模板](docs/SYNC_FIELD_MAPPING.md)
- [三节点配置、Windows启动及其他开发者接入](docs/SYNC_DEPLOYMENT.md)
- [本轮改动与测试报告](docs/SYNC_REPORT.md)

本机测试使用独立MySQL实例和虚构数据，不复制业务库。已取消跨节点密码转发/远程验证计划。三端只交换学生、班级、课程、任务和普通考勤结果；本机60秒密码签到成功后转换为相同格式，其他系统无需支持密码签到。记录允许跨节点基于准确版本和前一事件ID修正，冲突保留待处理。历史 `SYNC_DESIGN.md` 为草稿，实际接口字段以 `SYNC_PROTOCOL.md` 为准。

如教师创建任务遇到403，请核对Network响应、实际登录角色（`GET /api/auth/me`）、教学班归属及Origin。前端使用同源`/api`代理；当前后端仅允许localhost/127.0.0.1跨域来源。学校IP白名单将在部署阶段显式配置，不能通过开放角色或任意跨域绕过权限。

## 在学校电脑获取本分支

```powershell
git clone --branch feat/three-node-sync https://github.com/chenmaojie-degithub/kaoqin.git
cd kaoqin
mvn -B -ntp -f backend/pom.xml package
Push-Location frontend
npm.cmd ci
npm.cmd run build
Pop-Location
```

安装 Java 17、Maven 3.9+、MySQL 8 和 Node.js 后执行。构建产物与数据库不随 Git 分发。已有业务库仅按部署文档执行增量迁移；不要导入 schema.sql、data.sql 或测试夹具。`data.sql` 仅保留虚构演示账号，不包含真实班级名单。学校三节点的 IP、密钥生成、字段映射和启动方式详见 [三节点部署说明](docs/SYNC_DEPLOYMENT.md)。

## 技术栈

- 前端：Vue 3、Vite、JavaScript、Vue Router、Pinia、Axios、Element Plus、ECharts
- 后端：Java 17、Spring Boot 3、Spring Security、JWT、MyBatis-Plus、Maven
- 数据库：MySQL 8.0

## 项目结构

    student-attendance-system/
    ├── frontend/          Vue 管理端
    ├── backend/           Spring Boot REST API
    ├── database/
    │   ├── schema.sql     建库、表、索引和约束
    │   └── data.sql       测试账号和示例数据
    ├── README.md
    └── .gitignore

## 环境要求

- JDK 17
- Maven 3.9+
- MySQL 8.0+
- Node.js 20.19+ 或 22.12+
- npm 10+

Maven 编译目标固定为 Java 17。生产环境建议直接使用 JDK 17。

## 初始化数据库

在项目根目录依次执行：

    mysql -u root -p < database/schema.sql
    mysql -u root -p student_attendance < database/data.sql

schema.sql 会创建 student_attendance 数据库，并删除、重建本系统的同名表。不要直接对已有同名生产库执行。

如果数据库是在“课程生效周次”功能上线前创建的，不要重新执行 schema.sql；请备份后执行一次增量脚本：

    mysql -u root -p < database/migrate_schedule_week_range.sql

升级脚本会保留全部旧课表，并将旧记录设置为第 1～20 周有效。若当前环境学期总周数不是 20，请先修改脚本顶部的 `schedule_default_end_week`。

已有数据库增加密码签到功能时，再执行一次：

    mysql -u root -p < database/migrate_attendance_session.sql

建议创建独立数据库用户：

    CREATE USER 'attendance'@'localhost' IDENTIFIED BY 'your-strong-password';
    GRANT ALL PRIVILEGES ON student_attendance.* TO 'attendance'@'localhost';
    FLUSH PRIVILEGES;

主要关系：

- user 与 student、teacher 是一对零或一
- class_info 与 student 是一对多
- course_class 关联课程、班级、教师和学期
- course_schedule 保存教学班每周的时间与地点
- attendance_task 属于一个教学班
- attendance_session 关联考勤任务，保存教师发布的60秒签到密码及状态
- attendance_record 关联任务、学生和课程；手工录入只保存异常，密码签到会保存NORMAL状态和签到时间
- leave_request 属于学生，可选指定课程

唯一索引 attendance_record(student_id, course_id, attendance_date) 防止重复考勤，attendance_record(session_id, student_id) 防止重复签到。

## 后端配置

后端不会保存数据库密码和可用的 JWT 密钥。启动前必须提供环境变量。

PowerShell：

    $env:DB_URL = "jdbc:mysql://localhost:3306/student_attendance?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
    $env:DB_USERNAME = "attendance"
    $env:DB_PASSWORD = "your-strong-password"
    $env:JWT_SECRET = "replace-with-a-random-secret-at-least-32-bytes"
    $env:SERVER_PORT = "8080"
    $env:ACADEMIC_YEAR = "2026-2027"
    $env:SEMESTER_NAME = "第一学期"
    $env:SEMESTER_START_DATE = "2026-09-01"
    $env:SEMESTER_END_DATE = "2027-01-17"

配置项说明：

| 变量 | 必填 | 默认值 | 用途 |
|---|---|---|---|
| DB_URL | 否 | 本机 student_attendance | JDBC 地址 |
| DB_USERNAME | 否 | attendance | 数据库用户 |
| DB_PASSWORD | 是 | 无 | 数据库密码 |
| JWT_SECRET | 是 | 无 | JWT HMAC 密钥，至少 32 字节 |
| JWT_EXPIRATION_MINUTES | 否 | 480 | Token 有效分钟数 |
| SERVER_PORT | 否 | 8080 | 后端端口 |
| ACADEMIC_YEAR | 否 | 2026-2027 | 当前学年名称 |
| SEMESTER_NAME | 否 | 第一学期 | 当前学期名称 |
| SEMESTER_START_DATE | 否 | 2026-09-01 | 当前学期开学日期，格式 YYYY-MM-DD |
| SEMESTER_END_DATE | 否 | 2027-01-17 | 当前学期结束日期，格式 YYYY-MM-DD |

示例也可参考 backend/.env.example。项目会自动读取 `C:\Users\<用户名>\.codex\.env`，也可由终端、IDE 或部署平台直接注入同名环境变量；系统环境变量优先级更高。

启动后端：

    cd backend
    mvn spring-boot:run

打包和运行：

    mvn clean package
    java -jar target/attendance-backend-1.0.0.jar

接口根地址为 http://localhost:8080/api。

Swagger UI：

    http://localhost:8080/api/swagger-ui.html

## 前端启动

    cd frontend
    npm install
    npm run dev

浏览器访问 http://localhost:5173。

开发服务器会把 /api 代理到 http://localhost:8080。生产构建：

    npm run build
    npm run preview

如前后端不同域部署，将 frontend/.env.example 复制为 frontend/.env.local，并把 VITE_API_BASE_URL 设置为后端完整 API 地址。

## 测试账号

data.sql 中三个账号的密码都是 123456：

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | admin | 123456 |
| 教师 | teacher | 123456 |
| 学生 | student | 123456 |


测试密码采用 BCrypt 保存，仅用于本地演示。部署后应立即修改。

## 角色权限

- 管理员：账号、学生、教师、班级、课程、教学安排和全部考勤管理
- 教师：查看本人教学班，创建和录入考勤，查看相关记录，审批所教班级请假
- 学生：首页 Dashboard、考勤日历/列表、今日课程、通知中心、提交/撤销请假、个人资料与修改密码

前端菜单和路由会按角色过滤；后端 Spring Security 和业务数据范围会再次校验，前端隐藏菜单不作为安全边界。

## 主要接口

| 方法与路径 | 功能 |
|---|---|
| POST /api/auth/login | 登录 |
| GET /api/auth/me | 当前用户 |
| GET/POST/PUT/DELETE /api/students | 学生管理 |
| GET/POST/PUT/DELETE /api/teachers | 教师管理 |
| GET/POST/PUT/DELETE /api/classes | 班级管理 |
| GET/POST/PUT/DELETE /api/courses | 课程管理 |
| GET/POST/PUT/DELETE /api/teaching | 教学关系 |
| GET /api/teaching/semester-calendar | 当前学期、总周数、当前周和指定周日期 |
| POST /api/teaching/{courseClassId}/schedules | 新增带生效周次的课程安排 |
| PUT /api/teaching/{courseClassId}/schedules/{scheduleId} | 编辑整个范围、当前周或后续周次 |
| GET/POST /api/attendance/tasks | 考勤任务 |
| POST /api/attendance/sessions | 教师开启60秒密码签到 |
| GET /api/attendance/sessions/{id} | 教师查询签到人数和状态 |
| POST /api/attendance/sessions/{id}/close | 教师提前结束签到 |
| POST /api/attendance/check-in | 学生提交六位签到密码 |
| PUT /api/attendance/tasks/{id}/records | 批量保存考勤 |
| GET /api/attendance/records | 考勤记录 |
| GET/POST /api/leaves | 请假查询与提交 |
| DELETE /api/leaves/{id} | 学生撤销待审批请假 |
| PUT /api/leaves/{id}/approval | 请假审批 |
| GET /api/statistics/dashboard | 角色范围内统计 |
| GET /api/student-portal/dashboard | 学生首页聚合数据 |
| GET /api/student-portal/today-schedule | 学生今日课程与签到要求 |
| GET /api/student-portal/notifications | 学生通知中心 |
| GET /api/student-portal/profile | 学生个人资料 |
| PUT /api/student-portal/password | 学生修改密码 |

## 考勤记录与修改规则

- 教师手工录入“正常”不会新增记录；密码签到成功会保留一条带签到时间的 `NORMAL` 记录，已有签到凭证不会被批量录入误删。
- 学生端会根据考勤任务、所在班级、异常记录和已批准请假动态生成完整历史，因此仍能看到正常、迟到、早退、请假和缺勤。
- 任务停止时写入 `closed_at`。停止后 20 分钟内教师或管理员仍可修改，超过后由后端统一拒绝，无法通过绕过前端按钮修改。

## 请假同步规则

请假审批通过后，系统在同一事务内为时间范围内已经存在的匹配考勤任务创建或更新请假异常记录。同步查询与数据库唯一索引统一使用“学生 + 课程 + 日期”作为记录身份，已有缺勤等异常会直接更新为请假，不会重复插入。若考勤任务尚未创建，不生成孤立记录；之后查询完整考勤时仍会根据已批准请假动态识别请假状态。

## 课程排课规则

- 周课表按星期一至星期日、1 至 10 节展示。
- 每条课程时间安排保存 `start_week/end_week`，只在当前查看周位于该范围内时显示，不会为每一周复制课程记录。
- 新增或编辑课程时可选择开始周和结束周；结束周不得早于开始周，也不得超过当前学期总周数。
- 编辑已有安排支持“整个范围”“仅当前周”“从当前周开始”三种方式。后两种由后端事务自动拆分原周次范围。
- 学期周从周一到周日计算；开学日期所在教学周为第 1 周，即使开学日期不是周一也不会修改原始开学日期。
- 系统根据 `SEMESTER_START_DATE` 和 `SEMESTER_END_DATE` 动态计算总周数。学期开始前定位第 1 周，结束后定位最后一周，学期中自动定位当前周。
- 管理员和教师可选择合法的周次范围，但页面一次只渲染其中一周；学生默认定位当前周并以只读方式查看本班课表。
- 每周表头显示真实日期并轻量高亮今天；当天课程根据实际开始、结束时间显示“已结束、进行中、未开始”。
- 最小排课单位是连续两节：1-2、3-4、5-6、7-8、9-10；课程也可以连续占用多个课时块，例如 1-4 或 1-6 节。
- 前端会在提交前提示时间冲突；后端会按“学期 + 班级 + 星期 + 时间区间 + 周次交集”再次校验，并通过班级行锁防止并发请求产生重叠安排。时间相同但周次不重叠的课程可以正常保存。
- 教师只能新增或删除本人负责课程的时间安排；管理员可以管理全部安排，学生不能调用排课写接口。
- 历史数据如果已有冲突，周课表会显示警告，可在“安排列表”中核对并删除错误记录。

## 常见问题

### 登录提示用户名或密码错误

确认已执行 data.sql，且没有手动修改 BCrypt 字符串。三个测试账号密码均为 123456。

### 后端提示 JWT_SECRET 缺失

这是安全保护。按“后端配置”设置至少 32 字节的 JWT_SECRET 后重新启动。

### 数据库连接失败

确认 MySQL 8.0 已启动、3306 端口可访问、数据库用户拥有 student_attendance 权限，并检查 DB_URL 的时区参数。

### 浏览器请求返回 401

Token 可能已过期或 JWT_SECRET 在后端重启后发生变化。退出并重新登录。

### Maven 默认仓库没有写权限

可将本地仓库放到有写权限的位置：

    mvn -Dmaven.repo.local=./.m2 clean package

### 前端图表为空

图表读取真实数据库记录。先由教师创建任务并录入考勤，或导入 data.sql 中的示例记录。

## 构建验证

开发完成时已执行：

    mvn clean package
    npm run build
    npm audit --omit=dev

后端打包、前端生产构建均通过，npm 生产依赖审计为 0 个已知漏洞。
