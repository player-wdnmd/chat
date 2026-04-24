# Chat

一个基于 Spring Boot 3 + 原生静态前端的聊天应用，当前已经接入：

- MySQL 账号体系
- 注册 / 登录
- Bearer Token 鉴权
- 按用户隔离的聊天状态
- 本地文件型 skills
- 积分扣减

## 当前功能

- Java 21 + Spring Boot 3.3
- OpenRouter 聊天调用
- MySQL 持久化用户、登录会话、聊天状态
- 注册只需要账号名和密码
- 登录后才能访问后端 API
- 每次成功发起一轮聊天请求消耗 1 积分
- 积分为 0 时前后端都会阻止发送
- skill 从 `src/main/resources/skills` 动态加载
- 每个会话仅支持选择 1 个 skill
- 聊天记录按用户隔离保存，不再共用一个本地状态文件

## 数据库说明

### 建表 SQL

项目已经提供手动执行的建表 SQL：

`src/main/resources/db/schema-mysql.sql`

注意：

- 项目启动时不会自动执行这份 SQL
- 你需要先在 MySQL 中手动创建数据库并执行这份 SQL
- 数据访问运行时使用的是 MyBatis-Plus / MyBatis-Plus-Join，不在 service 里手写 SQL

### 默认管理员

建表 SQL 中已经包含默认管理员账号：

- 用户名：`admin`
- 密码：`admin123`
- 积分：`2147483647`（作为“无限积分”的约定值使用）

## 启动前准备

### 1. 创建数据库

例如：

```sql
CREATE DATABASE chat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 手动执行建表 SQL

执行：

`src/main/resources/db/schema-mysql.sql`

### 3. 设置环境变量

至少需要准备：

```bash
export MYSQL_URL="jdbc:mysql://localhost:3306/chat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="你的mysql密码"
export OPENROUTER_API_KEY="你的openrouter key"
```

## 启动

```bash
./mvnw spring-boot:run
```

如果端口冲突：

```bash
export SERVER_PORT=8081
./mvnw spring-boot:run
```

访问：

- [http://localhost:8080](http://localhost:8080)
- 或你指定的 `SERVER_PORT`

## 环境变量

### 数据库

- `MYSQL_URL`
  MySQL JDBC URL
- `MYSQL_USERNAME`
  MySQL 用户名
- `MYSQL_PASSWORD`
  MySQL 密码

### OpenRouter

- `OPENROUTER_API_KEY`
  必填，未提供时后端会拒绝调用模型
- `OPENROUTER_MODEL`
  可选，默认 `x-ai/grok-4.1-fast`
- `OPENROUTER_SITE_URL`
  可选，透传到 OpenRouter 的 `HTTP-Referer`
- `OPENROUTER_APP_NAME`
  可选，透传到 OpenRouter 的标题信息
- `OPENROUTER_TEMPERATURE`
  可选，采样温度
- `OPENROUTER_MAX_TOKENS`
  可选，单次回复最大 tokens
- `OPENROUTER_MAX_CONTEXT_MESSAGES`
  可选，请求时保留的最近上下文消息数

### 其他

- `CHAT_SKILLS_DIRECTORY`
  skill 扫描目录，默认 `src/main/resources/skills`
- `SERVER_PORT`
  服务端口，默认 `8080`

## 项目结构

```text
src/main/java/com/example/chat/
├── config/         # 数据源、HTTP 客户端、鉴权过滤器、请求追踪
├── controller/     # 认证、聊天、状态、skills 接口
├── entity/         # MyBatis-Plus 实体
├── mapper/         # MyBatis-Plus / MPJ Mapper
├── model/          # 请求/响应/运行时模型
└── service/        # 认证、积分、状态、skill、OpenRouter 调用

src/main/resources/
├── application.yml
├── db/
│   └── schema-mysql.sql
├── skills/
└── static/
```

## 认证与鉴权

### 认证方式

当前采用：

- 账号名 + 密码注册
- 账号名 + 密码登录
- 登录成功后返回 Bearer Token
- 前端把 Token 存在浏览器本地
- 后续所有受保护接口都通过 `Authorization: Bearer <token>` 调用

### 受保护接口

除了下面两个接口，其余 `/api/*` 都需要先登录：

- `POST /api/auth/register`
- `POST /api/auth/login`

## 积分规则

- 注册新账号默认积分为 `0`
- 每次发送消息前，后端先尝试原子扣减 `1` 积分
- 如果积分不足，直接拒绝发送
- 如果积分扣减成功但模型调用失败，会自动把这 `1` 分退回
- 当前不提供充值/获取积分渠道
- 你可以直接在 MySQL 里手动修改 `chat_user.points`

## Skills

### 目录约定

每个 skill 占一个目录，例如：

```text
src/main/resources/skills/
└── zhangxuefeng/
    ├── SKILL.md
    └── meta.json
```

### 必需文件

- `SKILL.md`
  真正注入给模型的 skill 正文

### 可选文件

- `meta.json`
  用于给前端提供更友好的 `name / slug / description`

### 当前规则

- 每个会话只能启用 1 个 skill
- skill 由后端扫描目录并返回给前端下拉框
- 发送请求时，后端会把 skill 包装成额外的 system prompt 注入模型上下文

## API 概览

### 认证

#### `POST /api/auth/register`

请求体：

```json
{
  "accountName": "admin2",
  "password": "admin123"
}
```

#### `POST /api/auth/login`

请求体：

```json
{
  "accountName": "admin",
  "password": "admin123"
}
```

#### `GET /api/auth/me`

返回当前账号名和积分。

### 聊天

#### `POST /api/chat`

请求头：

```text
Authorization: Bearer <token>
```

请求体：

```json
{
  "conversationId": "conversation-id",
  "skillIds": ["pig-zhangxuefeng"],
  "messages": [
    {"role": "system", "content": "你是一个专业、清晰、直接的中文助手。"},
    {"role": "user", "content": "老师你好"}
  ]
}
```

响应里会额外返回：

- `remainingPoints`

用于前端立即更新剩余积分。

### 状态

#### `GET /api/state`

读取当前登录用户自己的聊天状态。

#### `PUT /api/state`

覆盖保存当前登录用户自己的聊天状态。

### 技能

#### `GET /api/skills`

返回当前可选 skill 列表。

## 前端行为说明

- 未登录时先显示登录/注册卡片
- 登录后才加载聊天状态和 skills
- 左侧会显示当前账号名与积分
- 积分为 0 时发送按钮禁用，输入框也会同步禁用
- 如果后端返回“积分不足”，前端会立刻更新成不可发送状态

## 运行时实现说明

### 数据访问

运行时数据库访问使用：

- MyBatis-Plus
- MyBatis-Plus-Join

主要用途：

- 用户 CRUD
- token -> user 的 join 鉴权解析
- 用户聊天状态读写
- 积分扣减 / 返还

### 密码

密码使用 `BCrypt` 哈希存储，不在数据库中保存明文密码。

### Bearer Token

- 数据库中只保存 token 的 `SHA-256` 哈希
- 不直接保存明文 token
- 请求进入时通过 token 哈希反查当前用户

## 验证

常用检查命令：

```bash
node --check src/main/resources/static/js/app.js
./mvnw test
```

在我这次改动里，这两个命令都已经通过。
