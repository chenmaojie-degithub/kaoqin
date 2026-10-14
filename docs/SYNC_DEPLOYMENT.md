# Windows 三节点运行与其他开发者接入

## 本轮环境和隔离方式

已检查本机 MySQL 8.0.30、Node/npm；Java17和Maven3.9.9位于项目忽略目录 tmp/tools，全局 PATH 没有Java/Maven。原MySQL业务实例监听3306，当前账号只有 student_attendance 的权限。本轮没有修改其权限或业务数据。

复用已安装 mysqld，在独立数据目录、loopback端口启动测试实例，不需要Docker。本轮v2自动集成测试用3308；新版脚本实验实例使用3309；此前3307/3308的v1结果已被新版报告替代。三个后端端口固定8081/8082/8083，连接各自独立测试库。三节点可运行同一套后端做协议验证，不能据此宣称其他两套系统已经兼容。

## 构建与增量迁移

项目根目录下执行（正式安装Java/Maven后可直接用mvn）：

```powershell
mvn -B -ntp -f backend/pom.xml package
Push-Location frontend
npm.cmd ci
npm.cmd run build
Pop-Location
```

没有设置 SYNC_IT_DB_PORT 时，真实三节点集成测试明确跳过；其余27项测试运行。完整联调见下面命令。JAR生成在 backend/target/attendance-backend-1.0.0.jar。

首次安装同步辅助表执行 `database/migrate_sync.sql`；已有v1辅助表时只增量执行 `database/migrate_sync_v2.sql` 增加last_event_id。由部署人员选择目标库导入，本轮未在真实库执行。首次安装脚本只新增五张辅助表及一行发布状态，不包含 USE、DROP 或业务表修改。不要对既有库执行 schema.sql/data.sql/测试fixture。脚本不会由应用自动执行；SYNC_ENABLED 默认false，启用前必须完成迁移与配置。

## 创建本地实验环境

```powershell
# 只创建全新独立目录；目录或端口已存在时拒绝，不删除或重建数据库。
./scripts/sync/Initialize-Lab.ps1 -LabDirectory ./tmp/sync-lab-v2 -MySqlPort 3309
./scripts/sync/Start-ThreeNodes.ps1 -ConfigDirectory ./tmp/sync-lab-v2
./scripts/sync/Test-RunningNodes.ps1 -ConfigDirectory ./tmp/sync-lab-v2
# 结束测试，停止受管理后端及测试MySQL，保留所有测试库文件。
./scripts/sync/Stop-Lab.ps1 -LabDirectory ./tmp/sync-lab-v2
# 再次开发时复用相同数据库与配置，不重新初始化。
./scripts/sync/Start-Lab.ps1 -LabDirectory ./tmp/sync-lab-v2
```

Initialize-Lab.ps1 默认路径 tmp/sync-lab，支持 -LabDirectory、-MySqlBin、-JavaExe。它创建独立 mysqld（只绑定127.0.0.1、禁用mysqlx）、三个新库、三个仅有各自库权限的用户、三份本地配置及虚构账号。测试实例 root 空密码只用于此本地实验目录，不用于学校/生产；节点各用独立随机数据库密码、JWT密钥、节点凭据及签名私钥。

Start-Lab.ps1复用已初始化的mysql-data和配置，核对监听实例datadir后启动后端，不重建或导入fixture。

库名 attendance_sync_test_a/b/c；测试用户 sync_admin/sync_teacher/sync_student，测试密码均为 SyncTest!2026；仅包含 Fictional 姓名。每个库预配 CLASS_1、T001、COURSE_A/B/C。这些公开测试密码和 fixture 不能用于真实部署。

脚本从现有schema中只提取CREATE TABLE部分初始化新空测试库，不使用其建库/DROP部分，也不导入真实名单。Initialize-Lab拒绝3306和已占用端口，不调整现有MySQL服务。

Start-Node.ps1 的参数是 -ConfigFile、可选 -JavaExe/-Jar。Start-ThreeNodes 读取 node-a.local.json、node-b.local.json、node-c.local.json，隐藏启动Java进程，输出日志至配置目录；进程命令行不含密钥。启动时禁止加载共享 home/.codex/.env，以每份节点配置为准。Stop-Nodes根据PID、Java路径、JAR路径和nodeId检查进程身份后才停止，不停止其他Java/MySQL进程。

实验目录在 tmp/ 已忽略；另增加 `**/*.local.json` 和 `**/*.local.properties`，配置/密钥/进程状态不要提交。

## 真实 MySQL 自动化联调测试

需要一个专用于测试、绑定127.0.0.1的空密码root MySQL实例；可用上面实验实例的3309。测试拒绝3306，每轮用随机名称新建三个 attendance_sync_test_* 库，不删除旧库。测试期间8081—8083必须空闲：

```powershell
./scripts/sync/Stop-Nodes.ps1 -ConfigDirectory ./tmp/sync-lab-v2
$env:SYNC_IT_DB_PORT='3309'
& ./tmp/tools/apache-maven-3.9.9/bin/mvn.cmd '-Dmaven.repo.local=D:/Attendance/.m2' -B -ntp -f backend/pom.xml test
Remove-Item Env:SYNC_IT_DB_PORT
```

集成测试启动三个真实嵌入式HTTP后端，使用三套MySQL连接、真实Spring Security/JWT和虚构夹具。不同库自增ID刻意不同。结束自动关闭后端，保留测试库。不能指向学校数据库或既有业务实例。用于测试初始化的root连接与应用节点实际部署的独立库账号是两件事。

## 配置

SYNC_PEERS 只有URL不足以绑定节点ID、认证凭据和公钥，因此实现采用 **SYNC_PEERS_JSON**，不同时维护第二套地址配置。值为JSON数组，最多两个对端；URL必须含 /api、无查询参数/用户信息/片段。

| 变量 | 用途/默认 |
| --- | --- |
| SERVER_PORT | 每节点独立后端端口 |
| DB_URL / DB_USERNAME / DB_PASSWORD | 本机数据库连接，仅授予自己的库权限 |
| JWT_SECRET | 每节点独立JWT密钥，不与其他节点共享 |
| SYNC_ENABLED | 默认false；true启用捕获、接口和定时同步 |
| SYNC_NODE_ID | 稳定唯一NODE_A/B/C，首次启用绑定数据库 |
| SYNC_SCHOOL_ID | 三方共同学校身份命名空间 |
| SYNC_TOKEN | 本节点独立随机凭据，至少32字符 |
| SYNC_PRIVATE_KEY / SYNC_PUBLIC_KEY | 本节点Ed25519密钥对Base64 |
| SYNC_PEERS_JSON | 对端nodeId/url/token/publicKey数组 |
| SYNC_INTERVAL_MS | 默认5000，固定延迟而非叠加定时 |
| SYNC_TIMEOUT_MS | 默认2000，范围100—10000，覆盖连接与响应体 |
| SYNC_MAX_ATTEMPTS | 默认2，范围1—3，每轮每方向有限尝试 |

NODE_A示例（占位符须替换，不是可直接使用的密钥）：

```ini
SERVER_PORT=8081
DB_URL=jdbc:mysql://127.0.0.1:3306/attendance_node_a?serverTimezone=Asia/Shanghai
DB_USERNAME=attendance_node_a
DB_PASSWORD=<本机数据库密码>
JWT_SECRET=<仅NODE_A使用的JWT随机密钥>
SYNC_ENABLED=true
SYNC_NODE_ID=NODE_A
SYNC_SCHOOL_ID=SCHOOL_001
SYNC_TOKEN=<NODE_A独立随机凭据>
SYNC_PRIVATE_KEY=<NODE_A私钥Base64>
SYNC_PUBLIC_KEY=<NODE_A公钥Base64>
SYNC_INTERVAL_MS=5000
SYNC_PEERS_JSON=[{"nodeId":"NODE_B","url":"https://192.168.1.102:8082/api","token":"<NODE_B凭据>","publicKey":"<NODE_B公钥>"},{"nodeId":"NODE_C","url":"https://192.168.1.103:8083/api","token":"<NODE_C凭据>","publicKey":"<NODE_C公钥>"}]
```

| 节点 | 示例IP | 后端端口 | 独立数据库 | 对端 |
| --- | --- | --- | --- | --- |
| NODE_A | 192.168.1.101 | 8081 | attendance_node_a | B、C |
| NODE_B | 192.168.1.102 | 8082 | attendance_node_b | A、C |
| NODE_C | 192.168.1.103 | 8083 | attendance_node_c | A、B |

IP是配置示例，未探测学校网络。代码接受http/https，不自动部署TLS。学校可用内网证书/反向代理提供HTTPS；使用HTTP时节点凭据在网络上传输，必须限定可信隔离网络。不要公开数据库端口或把DB_URL改为对端数据库。

## 自动/手动同步

每5秒固定延迟执行一轮：重试待依赖事件→发布本地已提交日志→每个对端Push和Pull→再重试依赖。每个方向每轮最多一页100条，后续轮继续；手动按钮也使用相同页大小，积压多时可重复触发。AtomicBoolean防止定时任务与管理员操作并发重复执行，忙时返回started=false/reason=BUSY。

每个对端分别保存PUSH/PULL进度。失败当轮最多尝试SYNC_MAX_ATTEMPTS次，之后5、10、20、40…秒退避，最大300秒；手动触发可越过退避。超时/异常不推进游标，下一轮重发原eventId。空Push也实际认证对端，避免无数据时误报“连接成功”。后台HTTP响应体最大1MiB，有超时取消；同步SQL使用独立JdbcTemplate的5秒查询超时，不改变业务JdbcTemplate设置。

日志在有游标推进时记录成功，失败记录节点/方向/错误码；不打印payload、密码或token。管理员“数据同步”页显示本节点、地址、两方向最近通信/失败/退避时间、待推送事件、inbox状态计数及最近20个待处理/冲突错误。

网络故障不影响本地考勤；本地迁移/签名配置故障不能静默漏记事件，启用时会启动失败或在事件落库失败时回滚业务事务。需要暂时恢复普通单机运行时设置SYNC_ENABLED=false；关闭期间产生的数据不会自动补采，恢复前应评估人工补采需求。

## 其他两位开发者接入步骤

1. 先共同确定 schoolId、原样学号、课程代码、班级代码、教师编号、学期字段及任务所有权。独立准备本地学生账号和名单，不能靠同步密码实现统一登录。
2. 每方生成独立签名密钥/节点凭据，安全交换公钥及节点认证凭据；只保留自己的私钥。登记全部三个来源公钥，以验证转发事件。
3. 按 SYNC_PROTOCOL.md 实现请求/确认/Pull分页及签名；可复用 protocol-example.mjs 的无依赖Node标准库算法。先验证CLASS/COURSE/STUDENT参考映射和缺依赖持久化，不接触真实签到。
4. 实现各自的任务/学生/记录适配器，自己选择辅助表。无需复制本项目的业务表或自增ID。已有课程和班级匹配不上时应等待/冲突，不能自动建账号、授予权限或覆盖名单。
5. 将业务写入和出站事件放在同一事务；收到事件先保证幂等与持久化再确认；转发保持原签名和eventId。保证游标提交顺序安全。
6. 使用我方三个虚构账号与测试用例对接：重复、乱序、漏映射、同ID异内容、非法来源、签名篡改、断网恢复、业务唯一键冲突及普通记录基线冲突。
7. 跨节点密码输入转发、远程验证和要求对方实现密码签到的计划已取消。三方都只处理v2普通考勤结果；本机密码校验完成后由适配器转换为同样的记录。

实验环境的跨语言认证/签名探针：

```powershell
node ./scripts/sync/protocol-example.mjs --probe ./tmp/sync-lab-v2/node-a.local.json NODE_B
```

只允许fictional实验库和loopback对端；输出确认元数据，不输出私钥/节点凭据。

## 前端与学校部署范围

管理员菜单新增“数据同步”，浏览器始终向本机 `/api` 请求。学生/教师不能触发管理员接口；节点密钥不进入前端构建产物。

当前Vite代理默认为8080；单独调试节点A可设置 `$env:VITE_API_PROXY_TARGET='http://127.0.0.1:8081'` 后在frontend运行 `npm.cmd run dev`，也可在忽略的frontend/.env.local中配置该值。节点B/C分别改为8082/8083，浏览器仍通过同源/api访问。正式部署采用同源反向代理。三后端联调通过API进行，不代表已部署三份学校前端或证书/防火墙配置。不要通过开放JWT角色或任意CORS绕过部署配置。

GitHub发布在 feat/three-node-sync 独立分支进行，不合并 main。发布版本 database/data.sql 已移除真实班级名单，只保留虚构演示数据；原文件仅保存在本机被忽略的 backups/。此处理不会清除仓库已有历史中的名单；历史清理需要另行评审，不执行强制推送。

## 本轮通用协议对接资料

对外协议为v2：SYNC_PROTOCOL.md；可复制五实体样例sync-v2-examples.json；机器结构sync-protocol-v2.schema.json；字段/状态映射SYNC_FIELD_MAPPING.md及sync-field-mapping-template.csv。已取消远程密码验证计划。Test-RunningNodes脚本现在仅A使用本地密码签到，B/C使用普通考勤，三端都能自动同步结果。

旧v1实验目录需先在该独立测试库逐库运行migrate_sync_v2.sql，或另建全新v2实验目录；不能直接启动新版后端后假定旧辅助表自动升级。根本业务表不变。
