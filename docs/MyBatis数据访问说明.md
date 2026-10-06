# MyBatis 数据访问

所有业务数据库操作通过 MyBatis Mapper 执行，Service 负责权限校验、加解密、业务规则和事务，不再拼接或执行 SQL。前端接口、数据库表和既有数据不需要修改。

| Mapper | 负责的数据 |
| --- | --- |
| `AccountMapper` | 账号身份、登录时间、钱包初始化、测试账号初始化 |
| `PublisherUserMapper`、`StudentUserMapper` | 企业、学生实体查询及创建 |
| `JobMapper` | 岗位、报名、简历筛选、录取人数及取消/退出 |
| `ProfileMapper` | 个人资料、头像、发布/报名记录、企业查看简历 |
| `CompanyMapper` | 企业主页介绍和照片 |
| `WalletMapper` | 余额、流水、结算、提现与操作幂等记录 |
| `VerificationMapper` | 实名认证提交、审核状态和事件 |
| `ReviewAdminMapper` | 管理员账号、待审核列表、审核操作历史 |
| `NotificationMapper` | 消息发送、分页、未读数量与已读状态 |

Mapper 接口位于 `backend/src/main/java/cn/campus/jobs/mapper`，对应 XML 位于 `backend/src/main/resources/mapper`。Spring Boot 自动发现带 `@Mapper` 的接口，`application.yml` 中的 `mybatis.mapper-locations` 加载 XML。

企业和学生实体通过 XML 构造器映射生成 `PublisherUser`、`StudentUser` 和共用的 `AccountFields`。接口返回的 Map 保留数据库下划线字段名和空值字段，避免影响现有 Vue 页面。图片通过二进制类型映射读取。

所有请求参数均使用 `#{参数名}` 绑定。涉及企业/学生两个表的操作使用 XML `<choose>` 在固定表名和权限列之间选择，不接受外部输入拼接表名或 SQL。事务继续使用 Spring 的 `@Transactional`。报名及录取使用 MySQL 条件更新原子扣减剩余名额，退出、取消、支付等路径保留必要的 `FOR UPDATE` 行锁，详见 [报名名额并发控制](报名名额并发控制.md)。

本地缓存范围设置为 `STATEMENT`，并关闭二级缓存，避免事务内多次读取、行锁查询或响应 Map 的修改复用旧结果。数据库及 Redis 的 `.env` 配置与 Docker 启动方式保持不变。

Flyway 仍管理数据库初始化及版本升级。Flyway 的 Java 迁移和迁移历史识别代码使用其自身提供的 JDBC 连接；这是数据库升级机制，不经过业务 Mapper。测试中的 JdbcTemplate 仅用于准备测试数据和独立检查数据库结果，实际业务路径均通过 MyBatis 执行。
