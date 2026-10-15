# 学校部署与同学协作同步操作手册

更新：2026-10-15。适用分支：feat/three-node-sync。

## 本轮范围

只修改和发布我方系统，不登录或配置同学的电脑、不替他们生成密钥。当前开发电脑不需要拥有学校IP，也不调整当前网络或防火墙。学校现场才运行本手册中的网络相关命令。

| 电脑 | IP | 本轮处理 |
| --- | --- | --- |
| 我的学校电脑A | 10.32.54.192 | 本系统页面5173、后端8081；准备A的配置和启动脚本 |
| 同学B | 10.32.54.193 | 仅登记地址；接口端口、凭据及适配由同学提供 |
| 同学C | 10.32.54.172 | 仅登记地址；接口端口、凭据及适配由同学提供 |

B的8082、C的8083只是node-a.example.json中的建议端口，须现场确认，不代表已配置成功。三套系统各自拥有独立数据库，A是主要操作电脑，不是统一数据库服务器。

**看我的网页**：同学只需浏览器打开A地址，不需要部署前端或配置跨域。

**同步三套系统的数据**：同学系统也必须支持V2接口、认证/签名、字段适配和幂等处理；仅知道IP不能读取他们的MySQL。本文说明双方如何对接，不代替修改他们的项目。

## 一、现在在开发电脑准备

当前可用项目副本在D:\Attendance\student-attendance-system。学校可以放在D:\chen\kaoqin，所有新脚本按自身位置定位项目。

需要Java 17、Maven 3.9+、MySQL 8、Node.js 20.19+或22.12+及npm。GitHub不包含构建产物、数据库、node_modules或任何实际密钥。联网时提前下载构建依赖；课堂前端使用Vite联调模式，正式长期运行再配置同源反向代理。

首次下载：

```powershell
git clone --branch feat/three-node-sync https://github.com/chenmaojie-degithub/kaoqin.git D:\chen\kaoqin
cd D:\chen\kaoqin
mvn -B -ntp -f backend/pom.xml package
Push-Location frontend
npm.cmd ci
npm.cmd run build
Pop-Location
```

已有Git项目先检查git status，保留自己的修改后再更新，不要用reset或覆盖文件：

```powershell
git status
git fetch origin
git switch feat/three-node-sync
git pull --ff-only origin feat/three-node-sync
```

若分支切换/更新提示冲突，先处理本地修改，不强制覆盖。使用ZIP下载的目录没有.git，不能直接git pull。

## 二、只生成我方A配置

在项目根目录运行一次，可在开发电脑提前准备，也可到学校再运行：

```powershell
./scripts/sync/Prepare-SchoolConfig.ps1
```

只产生tmp/school-lan/node-a.local.json，不产生B/C配置或B/C密钥。文件内随机生成A的JWT密钥、节点凭据和Ed25519密钥对；遇到已有A配置拒绝覆盖。已有正式节点配置应继续使用，不能重新生成后替换签名身份。

初始SYNC_ENABLED=false、SYNC_PEERS_JSON=[]，可以先独立运行网站，不依赖同学系统和同步辅助表。到现场完成增量迁移、统一身份和凭据交换后，再开启同步。准备脚本不连接数据库，也不执行SQL。

用编辑器打开配置，修改以下三项为**A本机实际业务库**，不要照抄示例库名或密码：

```text
DB_URL       = jdbc:mysql://127.0.0.1:3306/你的实际库名?serverTimezone=Asia/Shanghai
DB_USERNAME  = 你的数据库账号
DB_PASSWORD  = 你的数据库密码
```

其余已准备：SERVER_ADDRESS=0.0.0.0、SERVER_PORT=8081、SYNC_NODE_ID=NODE_A、SYNC_SCHOOL_ID=SCHOOL_001、同步间隔5000ms。学校代码须由三方共同确认，首次启用后保持稳定。

node-a.local.json被Git忽略，不上传仓库。若要从开发电脑搬到学校，只私下携带A的这份配置；学校账号/密码不同则在学校修改。数据库密码、JWT_SECRET和SYNC_PRIVATE_KEY不能给同学，也不能复制到前端。

## 三、到学校让同学访问我的网页

先在A确认实际IPv4还是10.32.54.192；地址若变化，修改本手册对应配置再部署，脚本不会给电脑强行设置IP。

以管理员PowerShell在A项目目录执行一次：

```powershell
./scripts/sync/Open-SchoolFirewall.ps1
```

仅对本机学校IP开放5173给本地子网、8081给已知三节点IP；不关闭防火墙、不开放MySQL3306。脚本检查本机是否有学校IP，不在学校时会拒绝执行；本轮开发没有执行此脚本。学校终端隔离/安全软件拦截仍需学校网络管理员处理。

然后在普通PowerShell运行：

```powershell
./scripts/sync/Start-School.ps1
```

只启动A后端，确认就绪后启动A前端。首次前端缺依赖会执行npm ci，需要联网。保持前端终端打开。所有同学浏览器访问：

```text
http://10.32.54.192:5173
```

页面请求相对/api，由A前端代理到A后端127.0.0.1:8081。**浏览器不要直接请求localhost后端**，因为同学浏览器中的localhost指他们自己。

如果A后端已经运行，只需启动前端：

```powershell
./scripts/sync/Start-Web.ps1 -BackendPort 8081
```

当前开发电脑仍可访问http://localhost:5173；不必设置学校IP。本地启动后端可沿用现有配置，或使用已填写本地数据库的A配置运行Start-Node.ps1，不运行检查学校IP的Start-School.ps1。

退出前端后，停止本脚本管理的A后端：

```powershell
./scripts/sync/Stop-Nodes.ps1 -ConfigDirectory ./tmp/school-lan
```

### 跨域如何处理

网页监听0.0.0.0:5173，后端监听0.0.0.0:8081。同源/api代理是默认方式；A配置还允许精确Origin http://10.32.54.192:5173，并正确处理Authorization/Content-Type预检。JWT、角色和班级范围校验保留。

同学电脑IP是.193或.172，不意味着浏览器Origin是该IP：他们访问A网页时，Origin仍是http://10.32.54.192:5173，因此无须放开他们自己的页面地址。未来确需其他页面直接调用A业务接口，才在A的CORS_ALLOWED_ORIGINS中显式增加实际页面Origin并重启后端，不使用任意来源*。后端之间同步不受浏览器CORS限制，须验证节点凭据和事件签名。

## 四、我和两名同学先确认这些信息

每人填写自己的信息；B/C不必运行我的项目或使用我的表结构：

| 项目 | 我方A | 同学B填写 | 同学C填写 |
| --- | --- | --- | --- |
| nodeId | NODE_A | NODE_B | NODE_C |
| IP | 10.32.54.192 | 10.32.54.193 | 10.32.54.172 |
| 对外API根地址 | http://10.32.54.192:8081/api | http://10.32.54.193:实际端口/api | http://10.32.54.172:实际端口/api |
| schoolId | SCHOOL_001（共同确认） | 与A一致 | 与A一致 |
| 节点token | A自行生成，私下交换 | B自行生成并提供 | C自行生成并提供 |
| Ed25519公钥 | A自行生成，交换公钥 | B提供 | C提供 |
| 适配层 | 已实现 | 同学实现并验证 | 同学实现并验证 |

三方共同填写[字段映射模板](SYNC_FIELD_MAPPING.md)和[sync-field-mapping-template.csv](sync-field-mapping-template.csv)，确认原样学号、班级/课程代码、教师编号、学期及状态映射。同一学生不按姓名或数据库自增ID匹配。各系统需要预先有自己的学生账号和教学资料；同步不会传播登录密码或自动创建账号。

## 五、把A接到B/C

### 5.1 A的数据库仅增量迁移

先确认目标库并按学校流程备份。由数据库维护人员手工执行，本项目启动脚本不会执行。PowerShell不能使用Linux式输入重定向，建议进入MySQL交互终端：

```powershell
mysql -u 你的数据库账号 -p
```

在MySQL中确认目标库：

```sql
USE 你的实际库名;
SHOW TABLES LIKE 'sync_%';
-- 首次没有同步辅助表时执行：
SOURCE D:/chen/kaoqin/database/migrate_sync.sql;
-- 如果已经有V1辅助表，只执行以下增量升级，而不是重复初始化：
-- SOURCE D:/chen/kaoqin/database/migrate_sync_v2.sql;
```

不要执行schema.sql、data.sql或测试夹具，不删除业务表，不复制整库。新的V2辅助表已含last_event_id，已经升级的不必再做初始迁移。

### 5.2 交换节点信息，不交换私钥

在A导出可交给同学的节点接入描述（含节点认证凭据，需私下传递）：

```powershell
$a=Get-Content -Raw ./tmp/school-lan/node-a.local.json | ConvertFrom-Json
@{nodeId=$a.SYNC_NODE_ID;url='http://10.32.54.192:8081/api';token=$a.SYNC_TOKEN;publicKey=$a.SYNC_PUBLIC_KEY} |
    ConvertTo-Json | Set-Content ./tmp/school-lan/node-a.peer.local.json -Encoding utf8
```

这里只导出nodeId/url/token/publicKey，不把整个node-a.local.json交给同学。B/C各自提供相同四个字段，公钥为SPKI DER Base64（不是私钥或PEM文本）。将他们给的描述分别保存到A的tmp/school-lan/node-b.peer.local.json和node-c.peer.local.json，确认URL是实际端口。

双方必须互相登记：A登记B/C的凭据和公钥；B/C在各自后端登记A，转发事件还需要认识另一来源公钥。请求发送者使用自己的节点token，不能拿用户JWT代替；转发保留原事件签名。

A完成数据库迁移、确认三方学校代码后，再运行以下配置修改命令：

```powershell
$path='./tmp/school-lan/node-a.local.json'
$a=Get-Content -Raw $path | ConvertFrom-Json
$b=Get-Content -Raw ./tmp/school-lan/node-b.peer.local.json | ConvertFrom-Json
$c=Get-Content -Raw ./tmp/school-lan/node-c.peer.local.json | ConvertFrom-Json
$a.SYNC_PEERS_JSON=ConvertTo-Json -InputObject @($b,$c) -Compress
$a.SYNC_ENABLED='true'
$a | ConvertTo-Json -Depth 5 | Set-Content $path -Encoding utf8
```

停止A管理进程、关闭前端终端后，重新运行Start-School.ps1加载新配置。此后默认每5秒自动Push/Pull。管理员“数据同步”页面可查看状态和手动触发；浏览器只请求A，不接触节点密钥。

### 5.3 同学开发者需要实现的内容

对外标准接口只有两个：

```text
POST /api/sync/push
GET  /api/sync/pull?cursor=0&limit=100
```

以[Pull/Push V2协议](SYNC_PROTOCOL.md)、[JSON样例](sync-v2-examples.json)、[Schema](sync-protocol-v2.schema.json)为准。完成五类实体适配：CLASS、COURSE、STUDENT、ATTENDANCE_TASK、ATTENDANCE_RECORD；实现节点认证、Ed25519签名、事务持久化、事件去重、持久游标、断网重试和冲突保留。未知映射保留待处理，不能直接覆盖已有记录；修改须引用准确baseEventId及下一版本。

我的60秒密码签到保持本地验证，只把成功结果转换成普通考勤同步。两名同学不需要实现密码、会话或远程验证。JSON示例中的签名是占位值，发送前必须实际签名；不能直接复制示例假签名。Node.js标准库参考实现为scripts/sync/protocol-example.mjs，--probe只用于本地虚构实验环境，不直接对学校真实库运行。

## 六、学校现场共同验收

1. B/C打开A网页并登录，Network中API是A的/api；测试账号由各自管理员准备。
2. A执行Test-NetConnection 10.32.54.193 -Port 同学B实际端口及C的实际端口；同学反向验证A8081。TCP成功只证明端口可达，不能代替协议验收。
3. A管理员查看同步状态，先手动Push/Pull，再检查5秒自动同步。401查节点登记/token，400查协议版本/请求结构；签名失败还要检查Push逐事件确认中的拒绝原因，依赖缺失查映射。
4. 在独立测试库或获准的测试任务中，A/B/C分别产生普通结果，验证三方收敛和重复同步不重复。
5. 测试A→B→C转发，eventId和来源签名不变；临时停一个测试节点后恢复，验证补同步。
6. 同一学生/任务并发修改应保留冲突；缺学生/任务映射应保留待处理，不能删除事件。

同步页面连接成功不表示所有事件已应用，要检查待处理/冲突。不要为了通过测试取消JWT、改角色校验或关闭节点认证。

## 七、本次交付验证与边界

开发电脑完成本系统修改和构建验证，不给当前电脑绑定学校IP。学校启动和防火墙脚本保留给现场使用；真实业务库未修改。隔离三节点测试用于验证我方实现，不能替代B/C真实项目接入验收。

学校实网互访、B/C实际端口/密钥/字段映射及异构实现兼容性，需要三人现场完成。旧ZIP包含三机配置生成方式，已被本次GitHub文档和仅A的脚本替代，不再使用旧ZIP进行部署。

本轮发布前验证（2026-10-15）：Maven package成功，29项测试全部通过（0失败、0错误、0跳过，包含独立MySQL三节点）；前端生产构建成功，仅有分包体积/插件耗时提示。A页面Origin预检通过，未登录仍401、学生越权仍403；未配置的同学页面Origin和错误端口拒绝。仅A配置生成、拒绝覆盖已有密钥、当前非学校网络启动保护及全部PowerShell脚本语法检查通过。原有60秒密码签到及Pull/Push V2逻辑保留。
