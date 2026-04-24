# Chat

一个基于 `Spring Boot 3.3 + MyBatis-Plus + 原生静态前端` 的聊天与图片工具应用。

当前项目包含两条核心能力：

- 文本聊天：基于 OpenRouter 的对话能力，支持账号体系、会话状态、技能系统、积分扣减
- 图片工具：支持文字生图、图像处理（统一入口，底层分流到局部编辑或兼容转换）、图片历史持久化

项目整体是一个单体 Web 应用：

- 后端负责 API、鉴权、数据库读写、上游模型调用
- 前端直接由 Spring Boot 托管静态资源

---

## 功能概览

### 账号与认证

- 用户名 + 密码注册
- 用户名 + 密码登录
- Bearer Token 鉴权
- 退出登录会使服务端会话失效
- 会话支持空闲过期
- 登录 / 注册 / 兑换接口带服务端限流

### 聊天能力

- 文本聊天调用 OpenRouter，也可以自定义其他地址，只需要修改yml里面的base-url地址
- 聊天历史按用户隔离
- 会话状态持久化到 MySQL
- 每个会话最多启用 1 个技能
- 技能从数据库读取
- 每次成功聊天消耗积分
- 模型失败时自动退款

### 图片能力

- 图片相关能力是调用的RightCode中转站，转发的gpt-image-2，也可以自定义其他地址，只需要修改对应的base-url地址
- 文字生图
- 图像处理
  - 统一前端入口
  - 不勾选“仅修改局部区域”时，走图像转换
  - 勾选“仅修改局部区域”或上传遮罩图时，走局部编辑
- 图片结果按用户持久化到 MySQL
- 最近生成支持恢复、删除单条、清空全部
- 图片请求支持超时提示
- 图片上传有大小限制、MIME 白名单和频率限制

### 安全加固

- API 响应禁缓存
- 基础安全响应头
- Token 只在数据库中保存 SHA-256 哈希
- 输入校验覆盖用户名、兑换码、skillName 等关键标识字段
- 避免在日志中输出敏感长文本和部分明文内容

---

## 技术栈

- Java 21
- Spring Boot 3.3.x
- MyBatis-Plus
- MyBatis-Plus-Join
- MySQL 8+
- Hutool HTTP
- Lombok
- 原生 HTML / CSS / JavaScript

---

## 项目结构

```text
.
├── .mvn/
├── mvnw
├── mvnw.cmd
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/com/example/chat/
        │   ├── config/        # 配置类、过滤器、安全响应头
        │   ├── controller/    # 认证、聊天、状态、技能、图片、公共配置接口
        │   ├── entity/        # MyBatis-Plus 实体
        │   ├── mapper/        # Mapper 接口
        │   ├── model/         # 请求/响应模型
        │   └── service/       # 业务服务、积分、图片代理、历史、限流
        └── resources/
            ├── application.yml
            ├── db/
            │   └── schema-mysql.sql
            ├── prompts/
            │   └── default-skill-template.md
            └── static/
                ├── index.html
                ├── image-tools.html
                ├── css/
                └── js/
```

---

## 数据库初始化

### 1. 创建数据库

例如：

```sql
CREATE DATABASE chat
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 2. 执行建表 SQL

项目不会在启动时自动创建整套业务表。

请手动执行：

```text
src/main/resources/db/schema-mysql.sql
```

这份 SQL 会创建以下表：

- `chat_user`
- `chat_user_session`
- `chat_user_state`
- `chat_user_redeem_code_usage`
- `chat_user_skill`
- `chat_user_image_history`

### 3. 默认管理员

`schema-mysql.sql` 里已经包含默认管理员：

- 用户名：`admin`
- 密码：`admin123`
- 积分：`2147483647`（约定为“无限积分”）

---

## 快速启动

### 1. 准备环境变量

最少需要准备数据库和聊天模型配置：

```bash
export MYSQL_URL="jdbc:mysql://localhost:3306/chat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="你的数据库密码"

export OPENROUTER_API_KEY="你的 OpenRouter Key"
```

如果你还要使用图片工具，再补充：

```bash
export IMAGE_API_KEY="你的图片中转 Key"
export IMAGE_API_BASE_URL="https://right.codes/gpt/v1"
```

### 2. 启动项目

```bash
./mvnw spring-boot:run
```

如果端口冲突：

```bash
export SERVER_PORT=8081
./mvnw spring-boot:run
```

### 3. 访问地址

- 聊天页：`http://localhost:8080/`
- 图片工具页：`http://localhost:8080/image-tools.html`

---

## 配置说明

配置文件位于：

```text
src/main/resources/application.yml
```

推荐优先通过环境变量覆盖，不要把真实密钥直接写进 yml。

### 数据库

- `MYSQL_URL`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`

### 服务端

- `SERVER_PORT`

### 聊天能力（OpenRouter）

- `OPENROUTER_API_KEY`
- `OPENROUTER_MODEL`
- `OPENROUTER_SITE_URL`
- `OPENROUTER_APP_NAME`
- `OPENROUTER_TEMPERATURE`
- `OPENROUTER_MAX_TOKENS`
- `OPENROUTER_MAX_CONTEXT_MESSAGES`

### 图片能力

- `IMAGE_API_KEY`
- `IMAGE_API_BASE_URL`
- `IMAGE_API_MODEL`
- `IMAGE_API_MAX_UPLOAD_SIZE`
- `IMAGE_API_RATE_LIMIT_WINDOW`
- `IMAGE_API_RATE_LIMIT_MAX_REQUESTS`

### 项目级业务配置

这些配置在 `chat-app` 下，常用于调整业务规则：

- `CHAT_APP_UNLIMITED_POINTS`
- `CHAT_APP_UNLIMITED_POINTS_TOLERANCE`
- `CHAT_APP_REGISTER_DEFAULT_POINTS`
- `CHAT_APP_SESSION_MAX_IDLE`
- `CHAT_APP_LOGIN_RATE_LIMIT_WINDOW`
- `CHAT_APP_LOGIN_RATE_LIMIT_MAX_REQUESTS`
- `CHAT_APP_REGISTER_RATE_LIMIT_WINDOW`
- `CHAT_APP_REGISTER_RATE_LIMIT_MAX_REQUESTS`
- `CHAT_APP_REDEEM_RATE_LIMIT_WINDOW`
- `CHAT_APP_REDEEM_RATE_LIMIT_MAX_REQUESTS`
- `CHAT_APP_CHAT_COST_PER_REQUEST`
- `CHAT_APP_IMAGE_COST_PER_REQUEST`
- `CHAT_APP_IMAGE_HISTORY_LIMIT`
- `CHAT_APP_FRONTEND_AUTH_TOKEN_STORAGE_KEY`
- `CHAT_APP_FRONTEND_SYSTEM_PROMPT`
- `CHAT_APP_FRONTEND_DEFAULT_SKILL_TEMPLATE_LOCATION`

---

## 业务规则

### 积分

- 注册默认积分由 `chat-app.auth.register-default-points` 控制，默认 `0`
- 每次聊天成功消耗 `1` 积分
- 每次图片操作成功消耗 `20` 积分
- 如果聊天或图片调用失败，已经扣掉的积分会自动退回
- `2147483647` 被视为“无限积分”

### 兑换码

默认兑换规则：

- `VIP111` -> `10`
- `VIP222` -> `20`
- `VIP333` -> `30`
- `VIP444` -> `40`
- `VIP555` -> `50`
- `VIP666` -> `60`
- `VIP777` -> `70`
- `VIP888` -> `80`
- `VIP999` -> 无限积分

每个用户对同一个兑换码只能使用一次。

### 技能

- 技能存储在数据库表 `chat_user_skill`
- 每个技能属于一个用户
- 一个会话最多只启用一个技能
- 发送聊天请求时，技能会被注入为额外的 system prompt

### 图片处理

前端只有一个 `图像处理` 入口，但后端会自动分流：

- 如果用户只上传输入图片 + 指令，走兼容转换
- 如果用户勾选“仅修改局部区域”或上传遮罩图，走图片编辑

---

## 前端页面说明

### 聊天页

地址：

```text
/
```

主要功能：

- 登录 / 注册
- 历史会话列表
- 技能管理
- 兑换码
- 文本聊天

### 图片工具页

地址：

```text
/image-tools.html
```

当前包含两个入口：

#### 1. 文字生图

- 只输入文字提示词
- 直接生成一张新图

#### 2. 图像处理

- 上传一张输入图片
- 输入处理指令
- 可选勾选 `仅修改局部区域`
- 可选上传遮罩图

结果能力：

- 预览图片
- 下载图片
- 点击图片放大查看
- 最近生成按用户持久化

---

## API 概览

以下为主要接口。

### 公共配置

#### `GET /api/config/public`

返回前端运行时需要的公共配置，例如：

- token 存储 key
- 默认 system prompt
- 默认 Skill 模板
- 无限积分哨兵值

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

返回当前登录用户信息。

#### `POST /api/auth/redeem`

请求体：

```json
{
  "redeemCode": "VIP222"
}
```

#### `DELETE /api/auth/session`

服务端注销当前 Bearer Token 会话。

### 聊天

#### `POST /api/chat`

请求头：

```text
Authorization: Bearer <token>
```

请求体示例：

```json
{
  "conversationId": "conversation-id",
  "skillIds": ["1"],
  "messages": [
    {
      "role": "system",
      "content": "你是一个专业、清晰、直接的中文助手。"
    },
    {
      "role": "user",
      "content": "老师你好"
    }
  ]
}
```

返回内容包含：

- `model`
- `content`
- `requestId`
- `latencyMs`
- `remainingPoints`

### 聊天状态

#### `GET /api/state`

读取当前用户完整聊天状态。

#### `PUT /api/state`

保存当前用户完整聊天状态。

### 技能

#### `GET /api/skills`

读取当前用户技能列表。

#### `POST /api/skills`

创建技能。

#### `GET /api/skills/{skillId}`

读取技能详情。

#### `PUT /api/skills/{skillId}`

更新技能。

#### `DELETE /api/skills/{skillId}`

删除技能。

### 图片工具

#### `GET /api/images/meta`

返回图片工具元信息，例如：

- 是否已配置图片服务
- 单次消耗积分
- 上传大小限制
- MIME 白名单
- 限流规则

#### `GET /api/images/history`

读取当前用户最近图片历史列表。

#### `GET /api/images/history/{historyId}`

读取某条图片历史详情。

#### `DELETE /api/images/history/{historyId}`

删除某条图片历史。

#### `DELETE /api/images/history`

清空当前用户全部图片历史。

#### `POST /api/images/generations`

文字生图。

请求体示例：

```json
{
  "model": "gpt-image-2",
  "prompt": "生成一张重庆夏天傍晚的照片"
}
```

#### `POST /api/images/edits`

图片编辑，`multipart/form-data`。

字段：

- `model` 可选
- `prompt` 必填
- `image` 必填
- `mask` 可选

#### `POST /api/images/chat/completions`

图像转换，兼容风格接口。

请求体示例：

```json
{
  "model": "gpt-image-2",
  "role": "user",
  "content": [
    {
      "type": "text",
      "text": "改成中国水墨画风"
    },
    {
      "type": "image_url",
      "image_url": {
        "url": "data:image/png;base64,..."
      }
    }
  ]
}
```

---

## 安全说明

当前项目已经做了这些基础安全处理：

- Bearer Token 只存哈希，不存明文
- 会话支持空闲过期
- 支持服务端注销
- 注册 / 登录 / 兑换 / 图片请求限流
- 安全响应头
- API 响应禁缓存
- multipart 上传大小限制
- 图片 MIME 类型校验
- 关键输入字段有白名单校验

仍然建议你在生产环境继续加强：

- 使用 HTTPS
- 使用权限最小化的数据库账号
- 配置真实的反向代理和 WAF
- 对密钥统一走密钥管理平台，不直接落盘
- 视需要把登录态从 localStorage Bearer Token 改成 HttpOnly Cookie

---

## 常见问题

### 1. 为什么项目启动后没有自动建表？

这是设计如此。当前项目要求手动执行：

```text
src/main/resources/db/schema-mysql.sql
```

这样更适合开源场景和可控部署。

### 2. 图片上传报 1MB 超限怎么办？

需要同时确认两层限制：

- `spring.servlet.multipart.max-file-size`
- `image-api.max-upload-size`

当前默认都走：

```text
IMAGE_API_MAX_UPLOAD_SIZE=10MB
```

修改后需要重启服务。

### 3. 图片请求为什么会超时？

图片生成可能明显慢于文本请求。

当前默认图片读超时：

```text
image-api.read-timeout: 300s
```

如果中转站本身很慢，即使本地超时调大，也可能仍然失败。

### 4. 为什么图片历史恢复后看不到原始上传文件？

浏览器安全模型不允许把历史文件重新塞回 `<input type="file">`。  
项目当前恢复的是：

- 提示词
- 模型
- 结果图
- 结果摘要
- 原始响应 JSON

如果要再次处理，需要重新选择本地文件。

---

## 开发建议

如果你准备继续扩展这个项目，比较值得优先做的方向：

- 把登录态改成 HttpOnly Cookie
- 增加集成测试
- 增加数据库迁移工具（Flyway / Liquibase）
- 给图片工具加任务队列或异步轮询
- 补更清晰的监控和告警指标

---

## License

当前仓库已包含 `LICENSE` 文件，请以仓库根目录中的许可证声明为准。
