# 第二阶段方向调整：普通考勤同步验收报告

日期：2026-10-14。分支：feat/three-node-sync。保留此前未提交修改；未commit、Push或合并main。本轮未修改真实业务库或真实学生数据。

## 检查结论与修复

进入本轮时Pull/Push、自动同步、节点认证、签名、去重、重试已工作，但v1协议仍要求PASSWORD、会话UUID和60秒窗口；只有三类实体，记录还限制为任务owner修改。因此原实现不能直接满足另外两套不支持密码签到的系统。

本轮复用原同步模块，升级为protocolVersion=2：

- 对外标准接口保持 POST /api/sync/push 与 GET /api/sync/pull；原管理员JWT入口与自动服务保留。
- 共有JSON覆盖CLASS、COURSE、STUDENT、ATTENDANCE_TASK、ATTENDANCE_RECORD。
- 记录只含schoolId/studentNo/taskUuid/status/recordedAt/remark/baseEventId，不含PASSWORD、sessionUuid、openedAt、expiresAt、checkedInAt。
- 本机60秒密码生成、校验、服务器有效期、权限、班级及重复签到逻辑保留；成功后的普通结果由原事务事件挂接转换出站。
- 移除上一轮“远端签名凭证视为本地密码签到”的适配行为。普通NORMAL不认证本机密码；已有本机session_id/check_in_time不被远端普通修正覆盖。
- 考勤记录允许三端写入：version+前一已应用eventId精确匹配才顺序合并；并发、乱序依赖和唯一键冲突保留，不用时钟最后写入胜出。
- 学生/班级/课程是参考快照和身份映射，按统一代码绑定本地已有数据，不自动创建账号/转班/覆盖名称，无法映射时保留待处理。
- 保留旧v1原事件和签名；v2 Pull不输出密码专属旧事件，不兼容待处理事件留档为LEGACY_PROTOCOL_REVIEW。

跨节点密码转发、远程验证、要求另外两套系统实现密码签到的计划已取消，不再作为待开发事项。

## 修改范围

后端复用并更新SyncProtocol、SyncService、SyncStore、SyncWorker、SyncAttendanceAdapter、SyncCapture。SyncSecurityConfig、SyncCrypto、SyncController、SyncAdminController的认证/签名/路径主体沿用。AttendanceServiceImpl只撤回旧远程密码凭证兼容和普通记录owner限制；原密码校验与JWT角色/班级检查不重构。

数据库：更新首次安装migrate_sync.sql，并新增migrate_sync_v2.sql，只对辅助表增量增加last_event_id。已有receipt_verified列不删除，但v2不再用它证明密码签到。

协议交付：SYNC_PROTOCOL.md、sync-v2-examples.json、sync-protocol-v2.schema.json；映射交付：SYNC_FIELD_MAPPING.md、sync-field-mapping-template.csv。同步/部署文档、Node.js示例和三进程冒烟脚本同步更新。

## 实际测试结果

最终Maven package：**28项，失败0、错误0、跳过0，BUILD SUCCESS，JAR打包通过**。包含27项单元/MockMvc测试及1项多场景真实三节点HTTP+MySQL测试；若不设置SYNC_IT_DB_PORT，真实集成测试明确跳过。

Vue生产构建通过，保留原有大体积chunk提示；没有为本轮新增依赖或重构前端。

本轮真实集成测试使用原MySQL安装程序在独立3308实例创建三个随机名称测试库，后端8081/8082/8083。本地学生、教师、课程、班级、任务自增ID故意不同，只有Fictional测试数据。

| 验证 | 结果 |
| --- | --- |
| A本地密码成功→普通JSON | NORMAL/recordedAt出站，无密码/会话/60秒字段 |
| B/C普通考勤→A/B/C MySQL | B/C仅普通教师考勤，没有调用密码API，各端结果同步 |
| B修正A记录 | 原教师JWT及班级校验通过，自动同步ABSENT，task UUID/record UUID不变 |
| C提交后续普通LATE修正 | 正确baseEventId+版本，A应用并转发；不要求C支持密码 |
| 两节点同时基于旧版修正 | 并发HTTP请求一条APPLIED、一条CONFLICT；记录不增生，不直接覆盖 |
| 本地密码证据 | A原session_id/check_in_time保留；B/C普通同步记录checkedIn=false |
| 五实体映射 | CLASS/COURSE/STUDENT/TASK/RECORD都经过真实适配；不同自增ID仍匹配 |
| 缺学生/任务映射 | 原事件WAITING_DEPENDENCY持久化，补齐后可应用 |
| 已有唯一键冲突 | 原NORMAL保持，冲突事件留档 |
| 重复/转发 | 原eventId/origin/signature不变，A→B→C→A去重，记录不重复 |
| 非法签名与节点/JWT隔离 | 拒绝伪造，管理员JWT不能替代节点凭据，普通教师不能操作同步管理 |
| v1兼容隔离 | 旧密码事件存档，不在v2分页输出，不冒充普通结果 |
| 增量SQL升级 | 模拟v1辅助表，运行v2迁移两次，新增列正确且可重复执行 |
| 断网恢复与自动同步 | C关闭后A继续本地写入；C恢复后补齐；B新普通记录无需手动触发自动到三端 |
| 分页/事务 | 超过100事件分页不漏不重复；强制outbox写失败时业务同事务回滚 |
| 文档样例 | 五实体JSON通过实际Java结构校验器；额外密码字段被拒绝 |

另在独立3309实例新建sync-lab-v2，运行三个独立Java进程，Test-RunningNodes脚本只在A调用密码接口、B/C写普通考勤并自动收敛；Node.js→Java的v2签名/身份探针通过，并基于Pull取得的实际基线提交含中文/null字段的普通考勤修正，返回APPLIED、persisted=true。

复现命令和配置见SYNC_DEPLOYMENT.md。所有测试使用独立测试库，不执行真实库迁移。收尾停止本轮测试实例和后端，保留数据库文件。

收尾只读检查：原3306业务库仍为11张表、sync_*表数量0，原MySQL进程继续运行；8081—8083、3308/3309测试进程均已停止。

## 对接方现在需要做什么

1. 填写CSV字段映射、状态转换、统一代码和时区。
2. 实现两个节点接口及原始事件签名验证，正确保存eventId、各对端游标和已应用记录版本/事件基线。
3. 用自己的适配器绑定已有学生/班级/课程和任务，不复制对方自增ID。
4. 普通新增用version=1/baseEventId=null；修正用观察到的上一eventId和version+1；缺映射/并发冲突必须保存。

不需要实现密码签到、密码字段、会话表、远程密码验证或统一JWT密钥。

## 边界与未验证事项

- 另外两套真实项目尚未提供字段/API，实际字段映射需他们填写后联调；不能以三同后端代替异构系统验收。
- 学生/班级/课程是身份参考映射，本轮不做完整基础资料CRUD复制或自动创建账号。改学号/转班需共同协调。
- 永久冲突保留并展示，未新增冲突解决界面；历史v1数据补采/建立v2基线需单独评审，未自动重写或重建日志。
- 学校IP、HTTPS/防火墙及真实浏览器界面操作未实机验证。同步默认关闭，真实库增量SQL本轮未执行。

## GitHub发布前复验（2026-10-14）

Maven package成功，28项测试全部通过（含独立3309测试实例上的三节点HTTP/MySQL集成测试，0失败、0跳过）；Vue生产构建成功，只有分包体积提示。同步模块、管理员页面、增量SQL、统一协议/Schema/JSON样例、字段映射模板和Windows部署脚本一并发布。

发布版本移除了已跟踪初始化SQL中的真实班级名单；仅保留虚构演示数据，原SQL在本地忽略备份中保留。未执行真实业务库DDL/DML。忽略本地配置、私钥、导出备份、日志、测试数据目录和构建产物；个人工作交接文件不发布。已有Git历史未被改写。
