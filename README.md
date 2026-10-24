# rag-kit · 可独立运行的检索增强问答模块

一套**不依赖云端 API** 的 RAG（检索增强生成）最小实现：文档切块 → 本地向量化 → 余弦相似度 Top-K 检索 → 双提示词编排 → 本地大模型作答。全部在本地跑完，数据不出机器。

```
Spring Boot 3.5 · Java 17 · MySQL 8 · 本地 Ollama（bge-m3 向量 / qwen2.5:7b 对话）
```

---

## 为什么单独抽出来

这套代码原本是一个校园项目（8 人共有软著）里的问答模块。**整体项目不能由我一个人开源**，但检索问答这条线是我独立开发、且可以脱离原项目单独运行的部分，所以抽出来单独发布。

原项目特有的东西（登录鉴权、会话表、小程序接口）全部没有带过来 —— 这个仓库里只有检索与问答本身。

---

## 快速开始

**1. 准备 Ollama**

```bash
ollama pull bge-m3        # 向量模型，1024 维
ollama pull qwen2.5:7b    # 对话模型
ollama serve              # 默认 11434
```

**2. 建库**

```sql
CREATE DATABASE ragkit DEFAULT CHARACTER SET utf8mb4;
```

表结构不用手动建，`spring.jpa.hibernate.ddl-auto=update` 会自动生成 `knowledge_chunk`。

**3. 填本机密码**（这个文件被 git 忽略了，密码不会进仓库）

`application.properties` 里的 `spring.datasource.password` 是占位符 `CHANGE_ME`。
**直接启动一定失败**，会报 `Access denied for user 'root'@'localhost' (using password: YES)`。

另建 `src/main/resources/application-local.properties`，**只需要写这一行**：

```properties
spring.datasource.password=你本机的 MySQL root 密码
```

**4. 跑起来（必须激活 local profile，否则上一步那个文件不会被加载）**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

IDEA 里二选一：

- Run/Debug Configurations → **Active profiles** 填 `local`
- 或者 VM options 加 `-Dspring.profiles.active=local`

> Spring Boot 不会自动加载 `application-{profile}.properties`，必须显式激活对应 profile。
> 忘了这一步的典型症状就是：密码明明改了，却仍然报 `Access denied`。

服务端口默认 **8085**（`8080` 在不少 Windows 机器上会被 Steam 的 `steamwebhelper.exe` 占掉，
症状是 `Web server failed to start. Port 8080 was already in use`）。换端口改 `server.port` 即可。

**5. 打开浏览器测试台（最省事）**

服务起来后直接访问 **<http://localhost:8085/>** —— 仓库自带一个静态页面
（`src/main/resources/static/index.html`，由 Spring Boot 同源托管，不用配 CORS）。
页面上能灌文档、提问，直接看 `hit` / `degraded` 徽章和每次耗时，还有一张测试记录表方便对比命中与不命中。

> 必须通过 `http://localhost:8085/` 访问。直接双击 HTML 用 `file://` 打开会因为跨域失败。

想用命令行也可以：

```bash
curl -X POST http://localhost:8085/api/ingest \
  -H "Content-Type: application/json" \
  -d '{"documentId":"doc-001","title":"国家奖学金评审办法","text":"国家奖学金每生每年8000元，评审时间为每年9月……"}'

curl -X POST http://localhost:8085/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"国家奖学金多少钱？"}'
```

返回 `hit: true` 表示命中了知识库，`hit: false` 表示走了闲聊分支。

实测结果（本地 qwen2.5:7b）：

```json
{"chunks":1}
{"hit":true,"degraded":false,"answer":"国家奖学金的奖励标准为每生每年8000元。\n\n我的建议：……"}
{"hit":false,"degraded":false,"answer":"你好！最近咋样？学习生活还顺利吗？……"}
```

同一个问题命中与不命中**都调了模型**，输出的差别完全来自两套 prompt —— 这正是"双提示词"设计要的效果。

> **Windows 上 curl 的坑**：Git Bash 里直接 `-d '{"question":"中文"}'`，中文会被按 GBK 编码发出去，
> 服务端报 `JSON parse error: Invalid UTF-8 start byte 0xb9`。
> 把 JSON 存成 UTF-8 文件，用 `--data-binary @file` 提交即可。

**6. 用 Apifox / Postman 测（推荐）**

仓库根目录带了 `apifox-openapi.json`（OpenAPI 3.0.3），Apifox 里
**项目设置 → 导入数据 → OpenAPI/Swagger → 上传文件**，两个接口连同请求示例、字段说明、响应示例一起进来，
不用手敲。

三个容易卡住的点：

| 坑 | 现象 | 怎么办 |
|---|---|---|
| 没灌库就提问 | `hit` 永远是 `false`，以为检索坏了 | 顺序固定：先 `/api/ingest`，再 `/api/ask` |
| 请求超时 | 报网络错误 / 超时，但服务端日志正常 | 把 Apifox 请求超时调到 **300 秒**以上 |
| 第一次特别慢 | 首调卡一两分钟 | Ollama 在加载模型到内存，属正常；热了之后单次约 10 秒 |

结果看两个布尔字段就够了：`hit` 判断是否命中知识库，`degraded` 判断是否模型挂了走了降级
（`true` 说明没真的调到模型，这条不算测试通过）。

---

## 链路与分层

| 层 | 类 | 职责 |
|---|---|---|
| 工具 | `TextNormalizer` / `TextSplitter` / `CosineSimilarity` / `VectorJson` | 纯函数，无状态，拿来就能单测 |
| 向量 | `EmbeddingClient` | 调 `/api/embed`，批量文本 → 向量列表 |
| 入库 | `KnowledgeIngestService` + `KnowledgeChunkStore` | 规范化 → 切块 → 批量向量化 → **事务化**整篇替换 |
| 检索 | `KnowledgeRetrieveService` | 问题向量化 → 全表算余弦 → 降序 → 截 Top-K。**不做阈值过滤** |
| 模型 | `OllamaChatClient` | 只管跟 `/api/chat` 打交道 |
| 编排 | `RagQaService` | 检索 → 阈值 → 选提示词 → 调模型 → 降级 |
| 入口 | `RagController` | `POST /api/ask`、`POST /api/ingest` |

每个类只干一件事，所以每个类都能单测 —— 这也是全链路用构造器注入而不是 `@Autowired` 字段注入的原因。

---

## 几个刻意的设计决策

### 1. 检索层不做阈值过滤

`KnowledgeRetrieveService` 只负责"按相似度排序返回 Top-K"，**不回答"够不够格"**。
阈值判断放在编排层 `RagQaService`。

理由：阈值是会随业务调的策略，检索是通用能力。分开之后，推荐、去重、聚类这些场景可以直接复用检索服务，不用为了改阈值去动它。

### 2. 未命中也要调模型（这条是被实际使用打脸后改的）

初版设计成"检索没命中就返回固定话术、不调模型"，理由是省一次最长 60 秒的远程调用。

实际用下来翻车了：用户说「你好」也走这条路，被回一句"官方资料中暂时无法找到答案"。

**根因是把"库里没检索到"和"这个问题不该回答"划了等号。** 检索为空只说明知识库里没有，不代表模型不能正常陪用户聊天。

现在改成：命中与未命中**都调模型**，区别只在于用哪套 system prompt ——

- 命中用 `ANSWER_SYSTEM_PROMPT`：严格照资料说，数字一个字不许编；答完另起一段给建议，并注明"以下是我的建议，不是官方规定"
- 未命中用 `CHAT_SYSTEM_PROMPT`：保留人设正常聊天；一旦涉及具体政策（金额、条件、时间、流程）就坦诚说没查到

代价是每次多几秒延迟。这个代价值得。

顺带两条 prompt 写法上的血泪经验：必须**显式**写"不要出现'根据提供的文件''资料片段'这类说法"、必须**显式**写"不要引用片段编号" —— 不写，模型就会把 `buildUserPrompt` 里的 `【片段2】` 原样念出来。

### 3. 阈值 0.55 是实测出来的，不是拍脑袋

相关问题（"国家奖学金多少钱" 对上奖学金文件）相似度 0.63~0.75；
不相关问题（"图书馆几点关门" 对上奖学金文件）0.40~0.45。
取两组之间、偏保守一点，定 0.55。

### 4. 编排方法刻意不加 `@Transactional`

方法里有一次最长 60 秒的远程调用。包在事务里，数据库连接会在整个远程调用期间被占住，并发一上来连接池就垮。所以让每次落库各自成为短事务。

代价是不保证原子性，这个我接受。

### 5. 降级：两类异常共用一套话术

模型挂了（`RestClientException`）和检索时数据脏了（余弦计算抛异常）都返回同一句可读提示，不把堆栈抛给调用方。
用户只需要知道"服务出问题了"；具体是模型挂了还是库里数据坏了，靠 `error` 日志区分。

### 6. 换模型厂商只动配置层

`OllamaClientConfig` + `OllamaProperties` 是唯一知道"对面是谁"的地方，Service 只认识注入进来的 `RestClient`。
要把 Ollama 换成云端 API，改这两个类即可。

---

## 已知不足（按该做的优先级排）

1. **阈值硬编码** → 应外移到配置，再建一个小评测集（几十条问题 + 人工标注）反推最优值
2. **单轮，没有上下文** → 把历史问答拼进 messages 数组即可支持追问
3. **全量线性扫描** → 目前几百条块完全够用；上千条之后路线是：本地缓存 → ANN 索引（HNSW）→ 十万级再上 pgvector / Milvus
4. **没有流式输出** → 现在要等几秒整段返回，下一步改 SSE 逐字显示
5. **切分是定长窗口** → 会在句子中间断开；更好的做法是优先按段落/句号切，定长只作兜底

---

## 测试

```bash
mvn test
```

37 个用例，**不需要 MySQL、不需要 Ollama**，`mvn test` 一次全绿：

| 类 | 条数 | 说明 |
|---|---|---|
| `CosineSimilarityTest` | 8 | 同向 / 正交 / 反向 / 零向量 / 维度不一致 / null / 空 等边界 |
| `TextNormalizerTest` | 6 | 全半角、空白折叠、大小写等 |
| `TextSplitterTest` | 3 | 空串、2000 字中文的切分长度与首尾对齐、非法参数 |
| `KnowledgeIngestServiceTest` | 5 | Mockito 捕获入库实体，断言字段与条数 |
| `KnowledgeRetrieveServiceTest` | 5 | 断言排序、Top-K 截断、不做阈值过滤 |
| `OllamaChatClientTest` | 2 | `MockRestServiceServer` 钉死 `stream:false` 和取值路径 |
| `KnowledgeChunkRepositoryTest` | 3 | `@DataJpaTest` + 嵌入式数据库 |
| `RagQaServiceTest` | 5 | 命中/未命中的**提示词**断言、阈值边界、降级、参数校验 |

> 抽出来的是原项目 47 条用例中的 32 条，加上本仓库新增的 `RagQaServiceTest` 5 条，共 37 条。
> 剩下 15 条没带过来：8 条要连真实 MySQL 与本地模型（真实政策文档灌库、检索分数分布实测、
> 端到端问答冒烟），7 条绑定原项目的会话表（`AiServiceTest` 依赖 `ChatSession` / `ChatRecord`）。
> 抽出来时同步修掉了原项目里那条抄错期望值的用例（详见下一节）。

### 一条测试上的经验（二）

期望值能算出来就别手写。`1 / Math.sqrt(2)` 比 `0.7071` 好 —— 手抄浮点数迟早会漏一位，
而漏的那位会让测试变红，看起来像"余弦算错了"，实际是常量抄错了。

### 一条测试上的经验

`RagQaService` 的命中与未命中**都调模型**，所以 `verify(chatClient, never())` 已经彻底失效 —— 它对两条分支给出同样的结果。

**两条分支的行为靠参数区分时，断言目标要从"是否调用"转移到"传了什么"**，用 `ArgumentCaptor` 捕获 system prompt 做 `assertEquals`。

---

## 许可

MIT。见 [LICENSE](./LICENSE)。

原校园项目的软著（登记号 2025SR2541132）为 8 人共同著作权，本仓库只包含其中可分割使用的检索问答部分。
