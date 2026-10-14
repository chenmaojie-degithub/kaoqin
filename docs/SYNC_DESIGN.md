# 三节点增量同步方案（第二阶段设计草案，尚未实现）

> 本轮方向调整：跨节点密码转发/远程验证已取消。下文旧设想仅为历史记录，不能用作对接要求；当前只实现五类普通考勤数据，见SYNC_PROTOCOL.md和SYNC_FIELD_MAPPING.md。

> 历史草稿。当前已实现的字段、行为与运行方式请阅读 SYNC_PROTOCOL.md、SYNC_DEPLOYMENT.md 和 SYNC_REPORT.md；本文保留作为方案演进记录。

本方案以2026-10-14实际代码和本机只读数据库检查为基础。第一阶段仅修复签到与权限诊断，不新增同步表，不实施三节点部署，不推送 main。另外两套系统尚未提供，必须先共同确认协议，再分别实现适配器。

## 1. 现有结构与约束

```text
user ─ student ─ class_info
user ─ teacher
course + class_info + teacher ─ course_class ─ course_schedule
course_class ─ attendance_task ─ attendance_session
attendance_task + student + course ─ attendance_record
student + course(可空) ─ leave_request
```

本机 MySQL 8.0.30，实际存在11张表；签到记录已有 session_id、check_in_time，Session 已有过期时间和 OPEN/CLOSED/EXPIRED 状态。任务唯一键为 `(course_class_id, attendance_date)`；记录唯一键为 `(student_id, course_id, attendance_date)` 和 `(session_id, student_id)`。这些都是本地标识和约束，不可作为跨库 ID。

当前正常手工考勤可能没有实际记录。同步不能将“没有异常记录”转换为“密码签到成功”，不能删除旧的默认正常逻辑。跨节点成功签到必须有任务所属节点签发的回执和实际服务器签到时间。

当前数据库模型不能表达同一天、同课程、同学生的多条独立记录。若学校要求多节分别签到，需要另外评审这一限制。本版发生该本地唯一键冲突时进入待处理，不静默覆盖或擅自合并不同任务。

## 2. 协议和数据库适配器

```text
节点业务服务 → 本地事务事件日志 → REST/JSON协议 → 对端收件去重 → 对端数据库适配器
```

协议不出现本地 student_id、task_id、course_class_id 等自增 ID。每个系统实现同一协议的导出/导入适配器，将本地字段转换为统一实体，不能直接执行其他节点提供的 SQL。

第一版只处理 STUDENT_IDENTITY、ATTENDANCE_TASK、CHECK_IN_RECEIPT。课程/班级/教师先由学校统一代码进行人工映射，不做全量资料自动复制。缺少映射的任务暂停应用并明确报错，不能凭姓名自动建人、建班、建课程或授予教师权限。

### 身份和所有权

- node_id：部署时指定稳定且唯一的 NODE_A/B/C，更换电脑不能随意重置节点身份。
- 学生全局 ID：学校统一分配 UUID；首次导入通过学校代码 + 规范化学号匹配。学号作为字符串保留前导零，规范化规则由三方共同确定。姓名不能作为匹配键，学号纠错必须保留原全局 ID。
- 任务全局 ID：任务创建节点生成 UUID，并保存 owner_node_id。对已有任务进行一次性映射，不逐次导出时重新生成。
- 签到凭证业务键：`task_global_id + student_global_id`。事件 ID 为独立 UUID，同一凭证的重试必须保持同一事件 ID。
- 同步任务的所有权不能因接收节点而改变。不同节点创建的任务各自有所属节点；三方不能各自为同一个全局任务独立验证密码。
- 统一班级/课程/教师代码是导入前置条件，不代表其他两套数据库必须新增与本项目相同的业务表。

### 事件示例（仅虚构标识）

```json
{
  "protocolVersion": 1,
  "eventId": "29db4391-3140-4e95-a2bd-1bba46c068b1",
  "originNodeId": "NODE_A",
  "entityType": "CHECK_IN_RECEIPT",
  "entityId": "a0aa2cf6-249d-4ee3-b85c-7b5c9c54ddbf",
  "ownerNodeId": "NODE_A",
  "operation": "UPSERT",
  "entityVersion": 1,
  "occurredAt": "2026-10-14T02:00:00Z",
  "payload": {
    "taskGlobalId": "ebc21c8f-6cd8-49b4-bcc2-169760cc53ed",
    "studentGlobalId": "7e6d6eb9-c571-47a6-ad31-d3a45c7446d1",
    "checkInTime": "2026-10-14T02:00:00Z",
    "method": "PASSWORD",
    "receiptId": "a0aa2cf6-249d-4ee3-b85c-7b5c9c54ddbf"
  }
}
```

时间统一为带 Z 的 UTC ISO8601。学期日期/考勤日期仍是学校时区下的日期，协议另外声明学校时区。签到成功事件不自动覆盖教师之后人工判定的缺勤或已批准请假：密码签到凭证与考勤业务状态分开处理。

禁止字段：数据库连接信息、密码/密码哈希、JWT/签名密钥、JWT token、六位签到密码、手机号/邮箱等非同步所需信息。导出采用字段白名单，拒绝未知敏感字段，不能序列化整个数据库 Entity。

## 3. 建议辅助表

以下是设计，不是已执行的迁移。具体SQL在三方确认协议后提供增量脚本。

| 表 | 关键字段/索引 | 作用 |
| --- | --- | --- |
| sync_events | event_id唯一、origin_node_id、entity_type、entity_global_id、owner_node_id、operation、entity_version、payload JSON、payload_hash、published_seq可空唯一、created_at | 业务事务内写入事件；已提交事件按发布序号供Pull；保留收到后需要转发的原事件 |
| sync_inbox | event_id主键、payload_hash、status、error_code、received_at、applied_at | APPLIED/DUPLICATE/WAITING_DEPENDENCY/CONFLICT/REJECTED；防重放、记录待处理原因 |
| sync_cursor | peer_node_id + direction唯一、cursor、last_success_at、last_error、retry_at | 每个对端单独记录Pull和Push进度；保存断网重试状态 |
| sync_identity_map | entity_type + global_id唯一、entity_type + local_id唯一、business_key、owner_node_id、entity_version、updated_at | 全局ID映射本地ID与版本；身份business_key必要时另加唯一约束 |
| sync_publication_state | 单节点固定行、last_published_seq | 发布事务加行锁，串行分配连续可见序号，避免自动递增ID的提交乱序导致游标漏数据 |

不能直接以 updated_at 或未经保护的 AUTO_INCREMENT 事件ID为游标：并发事务可能后分配的ID先提交，先分配的ID后提交。发布步骤只扫描已提交未发布事件，并在同一个发布锁事务中分配序号、发布事件、更新 last_published_seq。

业务修改与事件写入必须同库事务提交；收到事件的业务应用、inbox去重标记、原事件转发日志也同库事务提交。重试不重新创建事件ID。出站某一节点成功不代表所有对端都成功，分别维护进度。

对端重复 event_id 但 payload_hash 不同，判为协议冲突；重复已处理ID且哈希一致，返回 DUPLICATE。WAITING_DEPENDENCY 不算已完成，映射补齐后可重试。

## 4. REST接口

三方统一根路径，认证后的调用者身份来自节点凭据，不能信任JSON中的 senderNodeId。

### POST /api/sync/push

请求：`{ protocolVersion, batchId, events: [...] }`，限制批次条数、请求字节数、字段长度和JSON层级。

响应：`{ results: [{eventId, status, errorCode}], serverTime }`。status包括 APPLIED、DUPLICATE、WAITING_DEPENDENCY、CONFLICT、REJECTED。身份验证失败返回401/403，整个请求格式不合法返回400。

逐事件事务应用，逐事件返回确认；响应丢失时可以重发相同批次。事件依赖顺序为学生映射→任务→凭证。发送端仅推进连续已确认的日志前缀，失败后的已成功事件允许重复发送。持久化待处理/冲突的事件也必须在status中可见，不能静默丢弃。

### GET /api/sync/pull?cursor=<opaque>&limit=100

响应：`{ servingNodeId, events, nextCursor, hasMore }`。cursor是本次提供数据节点的已发布序号，绑定节点身份与日志代次，不能混用A和B的游标。

接收者应用并持久化确认后，才推进对应对端游标。断线后从已确认位置继续。日志未完成保留/归档机制前不自动清理；请求过旧游标明确返回 CURSOR_EXPIRED，并提供人工恢复流程，不能悄悄返回空列表。

### GET /api/sync/status

返回本节点、协议版本、各对端最后成功时间/游标、未发送数、待依赖数、冲突数和脱敏错误；不返回节点密钥、签到码或完整学生数据。管理员可查看/操作本节点同步；节点凭据只能调用其被授权的节点接口。

### 手动Pull/Push入口

本节点提供管理员操作接口，例如 `POST /api/sync/peers/{peerId}/pull` 和 `/push`，管理员页面只增加小型同步面板。peerId从配置白名单选择，不接受任意URL，防止服务器被当作任意地址请求代理。后续定时器调用同一同步服务，不再实现另一套逻辑。

## 5. 合并与冲突规则

1. owner节点负责该实体版本递增；采用 owner + entity_version，不用各电脑时钟做最后写入胜出。
2. 旧版本忽略并明确确认；同版本同哈希去重；同版本不同内容、非owner修改都进入冲突。
3. taskGlobalId + studentGlobalId必须只有一个成功凭证。重复成功事件合并为同一凭证；内容不一致时保留已验证凭证并报冲突，不能改写签到时间为本节点当前时间。
4. 本地历史唯一键冲突不得通过删除旧数据解决。保留原记录，将事件置为CONFLICT；由管理员确认任务是否为同一业务任务并建立映射。
5. 接收后转发保持原eventId/origin/owner/版本，不生成新业务事件，A→B→C→A靠inbox去重终止环路。需要校验原owner签名，不能仅信任转发节点自报origin。
6. 第一版不传播物理删除；后续明确墓碑规则后支持逻辑删除事件。同步不提供数据库整库覆盖功能。

## 6. 方向调整：跨节点密码计划已取消

三方只同步普通考勤结果。本机60秒密码校验保持本地实现，成功后转换为共同的ATTENDANCE_RECORD；其他系统无需密码会话或远程验证接口。旧草稿中关于密码凭证、所属节点远程校验的设想不再开发。当前v2字段、五类实体映射和多节点记录版本规则以SYNC_PROTOCOL.md为准。

## 7. Windows本机部署预案（第三阶段才实施）

复用一套MySQL8服务，创建三个独立测试库和三个仅有各自库权限的账号，无需Docker。禁止导出/复制当前真实业务库作为测试数据，使用虚构测试账号与SQL。

| 节点 | 后端 | 前端 | 独立测试库 |
| --- | --- | --- | --- |
| NODE_A | 18081 | 15171 | attendance_sync_test_a |
| NODE_B | 18082 | 15172 | attendance_sync_test_b |
| NODE_C | 18083 | 15173 | attendance_sync_test_c |

本轮实际检查确认Node/npm、MySQL服务可用；Java/Maven不在PATH，但已忽略的tmp/tools内已有JDK17和Maven，本轮复用完成20项测试，无需重新下载。学校需要正式安装Java17/Maven或使用构建好的JAR。当前DB账号是否有创建三个测试库权限尚未验证；不提升现有账号权限，届时由有权限账号初始化测试库。

后续提供 PowerShell `start-nodes.ps1`、`stop-nodes.ps1`、测试库初始化脚本和虚构测试夹具；脚本固定校验测试库前缀，拒绝student_attendance，不默认删除数据库。先在本机三次运行同一代码验证协议，再用另外两套系统各自适配器做学校互通测试；前者不能证明异构系统已经兼容。

配置示例（规划字段，当前代码尚不支持SYNC_*）：

```dotenv
SERVER_PORT=18081
DB_URL=jdbc:mysql://127.0.0.1:3306/attendance_sync_test_a?serverTimezone=Asia/Shanghai
DB_USERNAME=sync_test_a
DB_PASSWORD=<仅在本节点本地配置>
JWT_SECRET=<每节点独立且足够长的随机值>
SYNC_NODE_ID=NODE_A
SYNC_PEER_B_URL=https://192.168.10.22:18082
SYNC_PEER_C_URL=https://192.168.10.23:18083
SYNC_NODE_CREDENTIAL=<独立节点凭据，不使用JWT密钥>
```

IP是示例，不是已探测的学校地址。优先让浏览器使用同源 `/api` 反向代理；需要跨域时仅配置实际学校前端来源。当前Vite代理固定8080，三节点脚本需将代理目标参数化，不能仅换后端端口。学校只开放必要应用端口给指定局域网，不公开MySQL端口。

## 8. 分步任务与验收

| 步骤 | 产物 | 验证门槛 |
| --- | --- | --- |
| 2.1 三方协议确认 | JSON Schema、错误码、身份/课程/班级映射规则、测试样例 | 两位开发者确认字段、owner验证、登录账号方案 |
| 2.2 本系统适配层与增量表 | 增量SQL、事务事件生成/导入、字段白名单 | 事件与业务同事务，旧正常/异常不受损；无敏感字段 |
| 2.3 Pull/Push/状态及认证 | 幂等收件、游标、重试、管理员手动入口 | 重复、乱序、依赖缺失、同ID异内容、环路、崩溃恢复、游标不漏数据 |
| 2.4 通用考勤适配（当前方向） | 普通结果、五实体映射、跨节点顺序修正 | 无密码依赖，基线冲突保留 |
| 3 本机三节点 | 三测试库/进程、隔离启动脚本、虚构夹具 | A/B/C分别产生日志，其余两端取得；断网恢复、重复同步、冲突全部验证 |
| 4 Git与学校部署 | README、迁移SQL、局域网配置、运行/排错说明 | 独立分支审查通过、密钥与真实数据排除、用户确认后Push |

冲突、依赖缺失和网络故障要分别统计，不能全部显示为“同步成功”。三机联调前必须拿到另两套系统的接口/字段样例及学校统一名单标识。

## 9. 提交风险检查

当前仓库的database/data.sql已被Git跟踪；其中学生名单须在Push前单独核实是否为真实数据。新增.gitignore只能阻止未跟踪文件，不能清除已跟踪内容或历史。第四阶段需要审查/必要时替换为虚构样例，并评估历史处理；本阶段不改写Git历史，也不新增真实学生导出。
