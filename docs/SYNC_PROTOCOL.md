# 三端普通考勤同步协议 v2

适用：三套功能相近、数据库结构不同的考勤系统。密码签到是我方系统的本地功能，其他系统不需要实现密码、会话、60秒窗口或远程验证。此前v1密码凭证协议不再作为对接协议。

## 对外只有两个节点标准接口

| 方法与路径 | 功能 |
| --- | --- |
| POST /api/sync/push | 上传统一业务事件，逐事件持久化、去重和合并，返回逐事件确认 |
| GET /api/sync/pull?cursor=0&limit=100 | 按本节点增量日志分页返回合法事件，包含可转发事件 |

请求必须携带 `X-Sync-Node: NODE_B` 和 `Authorization: Sync <NODE_B独立随机凭据>`。发送者须在本机对端配置名单中；Push的nodeId必须等于已认证节点。普通用户/管理员JWT不能替代节点凭据。

事件使用原始来源的Ed25519签名；转发不改eventId/originNodeId/payload/signature。各方需知道全部可能来源的公钥，只保留自己的私钥。签名用于证明业务事件来源，与是否支持密码签到无关。数据库密码、用户密码、JWT密钥、六位签到密码不在协议中。

浏览器只调用本机管理员JWT接口 `/api/sync/admin/status`、`/api/sync/admin/push`、`/api/sync/admin/pull`；这些不是另外两名开发者需要实现的节点标准接口，节点凭据不能调用它们。

## Push信封和普通考勤事件

```json
{
  "protocolVersion": 2,
  "nodeId": "NODE_B",
  "events": [{
    "eventId": "550e8400-e29b-41d4-a716-446655440000",
    "originNodeId": "NODE_B",
    "entityType": "ATTENDANCE_RECORD",
    "entityId": "<按taskUuid和学生统一标识计算的UUID>",
    "operation": "UPSERT",
    "version": 1,
    "occurredAt": "2026-10-14T10:30:01+08:00",
    "payload": {
      "schoolId": "TEST_SCHOOL",
      "studentNo": "S001",
      "taskUuid": "a50b3cd1-77be-4f55-b3c6-03fe88c7da00",
      "status": "NORMAL",
      "recordedAt": "2026-10-14T10:30:00+08:00",
      "remark": null,
      "baseEventId": null
    },
    "signature": "<Base64 Ed25519签名>"
  }]
}
```

该例是说明模板。可复制的五实体完整样例在 `sync-v2-examples.json`，UUID已按算法生成；其中signature为64字节全零的占位签名，发送前必须用自己的私钥签名，不能把占位签名当作合法事件。机器可读Push结构见 `sync-protocol-v2.schema.json`。示例文件由实际Java校验器测试。

根/事件未知字段拒绝；每批最多100条、请求/响应读取最多1MiB。operation仅UPSERT，UUID规范小写，version为1—1000000000整数。所有时间点明确Z或UTC偏移；考勤日期是Asia/Shanghai日期。空值字段必须显式写null。

## 五类共有实体

| entityType | payload完整字段 | 语义 |
| --- | --- | --- |
| CLASS | schoolId, classCode, name | 班级标准代码和展示名 |
| COURSE | schoolId, courseCode, name | 课程标准代码和展示名 |
| STUDENT | schoolId, studentNo, name, classCode | 学生统一学号和班级，不含账号密码/角色 |
| ATTENDANCE_TASK | schoolId, ownerNodeId, courseCode, classCode, teacherNo, academicYear, semester, attendanceDate, status, closedAt | 任务身份和教学关系；不包含签到会话 |
| ATTENDANCE_RECORD | schoolId, studentNo, taskUuid, status, recordedAt, remark, baseEventId | 三方都能处理的普通考勤结果及更新基线 |

字段细节：

- schoolId、学号和业务代码必须由三方共同确定，保留前导零和大小写。studentNo/classCode/courseCode/teacherNo仅ASCII字母、数字、下划线或横线，最多30字符；不匹配的本地代码由适配器维护显式映射，不能按姓名猜测。
- name最多100字符，是来源展示信息。接收端按代码绑定已存在的本地学生/班级/课程，不自动创建账号、转班或覆盖本地名称。名称不同不改变身份；学生班级不一致返回冲突。三个参考实体版本固定1，本轮是参考快照/身份映射，不是基础资料的全量CRUD复制。
- 任务status为OPEN/CLOSED；OPEN时closedAt=null，CLOSED时必须带时区时间。ownerNodeId=originNodeId；同一任务的元数据和状态由创建节点维护。teacherNo是共同教师业务编号，不是对方数据库teacher_id。
- 考勤status为NORMAL（正常）、ABSENT（缺勤）、LATE（迟到）、EARLY_LEAVE（早退）、LEAVE（请假）。各方应显式转换本地枚举，未知状态拒绝/待处理，不能默认为NORMAL。
- recordedAt是来源系统记录该结果的业务时间，允许null表示没有可靠时间；不用于最后写入胜出，也不表示密码验证。remark允许null/空字符串，最多500字符。
- baseEventId是**本次修改所依据的上一条已应用考勤事件ID**，新记录为null。修改先读取/同步当前记录，使用它的eventId和version，不猜测基线。
- 协议没有method、PASSWORD、sessionUuid、openedAt、expiresAt、checkedInAt或checkedIn；加入这些字段会被拒绝。接收NORMAL只更新普通考勤状态，不产生本地密码签到凭证。

## 全局标识，不复制自增ID

```text
CLASS UUID   = nameUUID(UTF8(schoolId + "\nCLASS\n" + classCode))
COURSE UUID  = nameUUID(UTF8(schoolId + "\nCOURSE\n" + courseCode))
STUDENT UUID = nameUUID(UTF8(schoolId + "\n" + studentNo))
TASK UUID    = 创建节点生成并永久保存的随机UUID
RECORD UUID  = nameUUID(UTF8(taskUuid + "\n" + STUDENT UUID))
```

nameUUID与Java UUID.nameUUIDFromBytes一致：完整输入做MD5，设置UUID v3及RFC variant位，输出小写字符串，没有额外namespace前缀。这是身份命名算法，不用于密码/认证。Node.js实现见protocol-example.mjs；.NET不要直接使用Guid默认混合字节序。

eventId每次新的业务变更生成随机UUID；重试必须复用原eventId。同一任务同一学生的RECORD UUID不随记录状态或来源节点改变。节点本地ID只保存在各自映射表中。

## 签名算法（不依赖密码签到）

私钥采用标准Base64的PKCS#8 DER，公钥采用标准Base64的X.509 SPKI DER。Java17的GenerateKeys.java或Node.js标准库均可生成Ed25519密钥。

签名时从事件对象删除signature字段，递归按对象属性名的UTF-16字典顺序排序，数组顺序不变；输出紧凑JSON，保留payload中的null，以UTF-8编码。字符串按JSON标准转义，普通非ASCII字符直接保留，不额外转义斜线。用Ed25519签名字节串，将64字节结果标准Base64填入signature。协议只有整数版本和字符串/null数据，没有浮点规范化问题。签名之后不能改写时间字符串、空值或备注。

参考实现为scripts/sync/protocol-example.mjs中的canonical/signEvent，以及Java SyncCrypto。内容SHA-256使用包含signature的同样规范化JSON，重复eventId内容不同会被发现。publicKey可交换，privateKey只能留在自己的服务器，不能复制其他节点的签名私钥。

## 多节点更新与冲突保护

记录允许由任何已认证、签名可信的节点产生/修正，不再要求记录作者等于任务所属节点：

1. 新记录：version=1、baseEventId=null；学生/任务映射齐全且无本地唯一键冲突才应用。
2. 顺序修改：version=当前版本+1且baseEventId=当前已应用eventId，才能更新同一映射记录。originNodeId记录本次修改作者，可由A改为B/C。
3. 缺前一版本：WAITING_DEPENDENCY / PREVIOUS_VERSION_MISSING，保存后等依赖补齐再重试。
4. 同版本不同eventId、不同内容，或更高版本基线不匹配：CONFLICT。两个节点同时基于同一旧版修改时不能互相覆盖；保留双方事件与已有业务状态，等待处理。
5. 重复eventId且内容一致：DUPLICATE，不重复应用。重复ID但内容不同：EVENT_ID_COLLISION、persisted=false，不覆盖原事件。
6. 本地已有记录但没有同一全局映射，或触发学生+课程+日期唯一键：CONFLICT。不能删掉旧记录后强行导入。

各系统负责在产生事件前检查自己的教师/管理员角色、班级权限及本地编辑窗口；接收端独立校验学生实际班级和任务映射。节点凭据仅存在后端，不给普通用户伪造事件的权限。

本机密码签到成功后，仍在原事务中保存本地session_id/check_in_time，再由适配层输出同样的NORMAL/recordedAt普通结果。其他系统不需要知道本机如何得出结果。远端修正本机同一条记录时，只更新普通status/remark，保留本地session_id/check_in_time；不能凭远端普通结果伪造、删除或改写密码凭证。

## Push确认与错误

```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "protocolVersion": 2,
    "nodeId": "NODE_A",
    "results": [
      {"eventId": "550e8400-e29b-41d4-a716-446655440000", "status": "APPLIED", "persisted": true, "errorCode": null}
    ],
    "serverTime": "2026-10-14T02:30:02Z"
  },
  "timestamp": "2026-10-14T10:30:02"
}
```

外层code=0仅表示请求成功处理。results与请求顺序一致，必须逐条查看：

| status | 含义 | persisted |
| --- | --- | --- |
| APPLIED | 业务、映射、原事件日志与inbox同事务提交 | true |
| DUPLICATE | 相同事件已保存，或参考实体已有相同身份映射 | true |
| STALE | 旧记录/任务版本不再应用，仍保留合法原事件 | true |
| WAITING_DEPENDENCY | 缺学生/班级/课程/教学班/任务或前一版本，原事件保存待重试 | true |
| CONFLICT | 身份、版本基线或业务唯一键冲突，原事件保存 | true；ID内容碰撞例外 |
| REJECTED | 签名无效，原事件保存在inbox但不传播 | true |
| FAILED | 持久化失败，不能确认已收到 | false |

空errorCode可能省略。只有事务已提交后才确认persisted=true。结构不合法的整个批次在入库前拒绝，不算收到；调用方必须修正而不是推进游标。依赖/冲突已保存可以推进传输进度，但不等于业务应用成功，必须在管理界面保留待处理状态。

HTTP：400/40000协议、字段或游标无效；401/40100节点认证失败；413/41300批次过大；503/50300同步关闭；意外存储失败可能返回500或逐事件FAILED。错误不返回数据库异常或密钥。

## Pull分页

```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "protocolVersion": 2,
    "nodeId": "NODE_B",
    "events": [{"cursor": 7, "event": {"eventId": "...", "originNodeId": "NODE_A", "entityType": "ATTENDANCE_RECORD", "entityId": "...", "operation": "UPSERT", "version": 2, "occurredAt": "...Z", "payload": {"schoolId": "...", "studentNo": "...", "taskUuid": "...", "status": "LATE", "recordedAt": null, "remark": null, "baseEventId": "..."}, "signature": "..."}}],
    "nextCursor": 7,
    "hasMore": false
  }
}
```

此处省略号是结构示意。limit为1—100，首次cursor=0，游标分别绑定每个提供数据节点。events按cursor严格递增，游标可有间隔，消费者不能自行计算cursor+1；nextCursor取最后返回项，空页保持原值。超过当前日志头的游标返回400。

提供端为已提交事件单独串行分配published_seq，避免直接使用自增journal_id产生提交乱序漏数据。接收端将页面事件全部持久化后才推进该对端游标；部分失败保留旧进度重拉，由eventId去重。每轮自动/手动每方向一页，hasMore时下一轮继续。

Pull可以转发已合法处理的他方原事件，保留原签名和来源；不转发未应用的依赖/冲突/拒绝事件。回到来源后去重终止环路。第一版不清理日志，节点重置/日志截断/灾难恢复需要协调。

## v1升级与本轮范围

协议版本提升为2以明确去掉密码耦合。未执行任何真实库迁移。首次启用用migrate_sync.sql；已经有v1辅助表时只增量执行migrate_sync_v2.sql增加last_event_id，不删除旧表、旧事件或业务数据。

旧v1密码事件原样留档，不改eventId或签名，也不会从v2 Pull泄漏密码专属字段；旧不兼容待处理事件保留为REJECTED / LEGACY_PROTOCOL_REVIEW，不自动当作普通签到应用。v1学生映射不能冒充新增的STUDENT快照。测试阶段建议使用新的独立v2实验库；旧历史数据补采/建立v2基线应单独评审，不能偷偷重放覆盖。

跨节点密码转发、远程验证和要求其他系统实现密码签到的计划已取消。本轮不新增这些接口、不要求对方修改签到方式，也不做统一登录或基础资料自动创建/覆盖。其他开发者只需转换五类共有数据和实现两个标准接口。
