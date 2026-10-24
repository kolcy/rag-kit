# rag-kit

不依赖云端 API 的检索增强问答（RAG）最小实现：文档切块 → 本地向量化 → 余弦相似度 Top-K 检索 → 双提示词编排 → 本地大模型作答。全链路在本机完成，数据不出机器。

```
Spring Boot 3.5 · Java 17 · MySQL 8 · Ollama（bge-m3 向量 / qwen2.5:7b 对话）
```

## 效果

服务内置一个浏览器测试台（`src/main/resources/static/index.html`，由 Spring Boot 同源托管）。启动后访问 **<http://localhost:8085/>** 即可灌入文档、发起提问，并直接观察命中状态、是否降级与本次耗时，页面下方保留一张测试记录表用于对比命中与未命中的差异。

一次真实调用（问题命中知识库）：

```http
POST /api/ask
{"question":"国家奖学金多少钱？"}

{"hit":true,"degraded":false,"answer":"国家奖学金的奖励标准为每生每年8000元。……"}
```

命中与未命中都会调用模型，两者输出的差异来自两套 system prompt，见「设计决策 2」。

## 快速开始

**1. 准备 Ollama**

```bash
ollama pull bge-m3        # 向量模型，1024 维
ollama pull qwen2.5:7b    # 对话模型
ollama serve              # 默认监听 11434
```

**2. 建库**

```sql
CREATE DATABASE ragkit DEFAULT CHARACTER SET utf8mb4;
```

表结构无需手动创建，`spring.jpa.hibernate.ddl-auto=update` 会自动生成 `knowledge_chunk`。

**3. 配置本机密码**

`application.properties` 中的 `spring.datasource.password` 是占位符 `CHANGE_ME`。另建 `src/main/resources/application-local.properties`（已被 `.gitignore` 忽略），写入：

```properties
spring.datasource.password=你本机的 MySQL root 密码
```

**4. 启动**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

IDEA 中在 Run/Debug Configurations 的 **Active profiles** 填入 `local`。服务默认监听 **8085**。

随后访问 <http://localhost:8085/> 打开测试台。若需要用 Apifox 或 Postman 调试，导入仓库根目录的 `apifox-openapi.json`（OpenAPI 3.0.3）即可获得两个接口及其请求示例。

<details>
<summary>启动与调用中的常见问题</summary>

| 现象 | 原因 | 处理 |
|---|---|---|
| `Access denied for user 'root'@'localhost'` | `application-local.properties` 未加载 | 必须显式激活 `local` profile。Spring Boot 不会自动加载 `application-{profile}.properties`，配置改了却未生效是最常见的排查点 |
| `Port 8080 was already in use` | Windows 上 8080 常被 `steamwebhelper.exe` 占用 | 修改 `server.port`，本仓库默认为 8085 |
| 首次 `/api/ask` 卡一两分钟 | Ollama 正在把模型加载进内存 | 属正常现象，预热后单次约 10 秒；调试时建议把客户端超时调到 300 秒以上 |
| `JSON parse error: Invalid UTF-8 start byte` | Git Bash 下 curl 把中文按 GBK 编码发出 | 将 JSON 存为 UTF-8 文件，用 `--data-binary @file` 提交 |
| 测试台空白 / 请求失败 | 通过 `file://` 直接打开了 HTML | 必须经 `http://localhost:8085/` 访问 |
| `hit` 始终为 `false` | 尚未灌入知识库 | 调用顺序固定：先 `POST /api/ingest`，再 `POST /api/ask` |

响应中两个布尔字段的判定含义：`hit` 表示是否命中知识库，`degraded` 为 `true` 表示模型未调通、返回的是降级话术，此种情况不计入有效测试。

</details>

## 链路与分层

| 层 | 类 | 职责 |
|---|---|---|
| 工具 | `TextNormalizer` / `TextSplitter` / `CosineSimilarity` / `VectorJson` | 纯函数，无状态，可直接单测 |
| 向量 | `EmbeddingClient` | 调用 `/api/embed`，批量文本转向量列表 |
| 入库 | `KnowledgeIngestService` + `KnowledgeChunkStore` | 规范化 → 切块 → 批量向量化 → 事务化整篇替换 |
| 检索 | `KnowledgeRetrieveService` | 问题向量化 → 全表计算余弦 → 降序 → 截取 Top-K，不做阈值过滤 |
| 模型 | `OllamaChatClient` | 封装与 `/api/chat` 的交互 |
| 编排 | `RagQaService` | 检索 → 阈值判断 → 选择提示词 → 调用模型 → 异常降级 |
| 入口 | `RagController` | `POST /api/ask`、`POST /api/ingest` |

各层职责单一，因此均可独立单测。这也是全链路采用构造器注入而非 `@Autowired` 字段注入的原因。

## 设计决策

### 1. 检索层不执行阈值过滤

`KnowledgeRetrieveService` 只负责按相似度排序并返回 Top-K，不判断是否足够相关；阈值判定上移至编排层 `RagQaService`。

阈值属于随业务调整的策略，检索属于通用能力。分离后，推荐、去重、聚类等场景可直接复用检索服务，无需为调整阈值修改检索层。

### 2. 命中与未命中均调用模型

初版设计为「检索未命中时返回固定话术、不调用模型」，动机是省下一次最长 60 秒的远程调用。投入使用后该设计暴露出问题：用户问候语同样走未命中分支，收到的却是「官方资料中暂时无法找到答案」。

根因在于把「知识库中未检索到」与「该问题不应回答」等同起来。检索结果为空仅说明知识库覆盖不足，并不意味着模型不能正常回应用户。

当前实现为两条分支都调用模型，差异仅在 system prompt 的选择：

- 命中时使用 `ANSWER_SYSTEM_PROMPT`：严格依据资料作答，数字不得编造；答复后另起一段给出建议，并声明建议不属于官方规定
- 未命中时使用 `CHAT_SYSTEM_PROMPT`：保持人设正常对话；一旦涉及具体政策（金额、条件、时间、流程），坦诚说明未检索到

代价是每次请求多出数秒延迟；相较之下，误伤正常对话对体验的影响更大，因此接受该代价。

提示词中有两条约束需要显式声明：禁止出现「根据提供的文件」「资料片段」一类表述，禁止引用片段编号。若不明确写出，模型会将 `buildUserPrompt` 中的 `【片段2】` 原样复述给用户。

### 3. 阈值 0.55 的取值依据

相关问题（如「国家奖学金多少钱」对应奖学金文件）相似度为 0.63~0.75；不相关问题（如「图书馆几点关门」对应奖学金文件）为 0.40~0.45。取两组区间之间并偏向保守，定为 0.55。

### 4. 编排方法不加 `@Transactional`

编排流程中包含一次最长 60 秒的远程模型调用。若将其纳入事务，数据库连接会在整个远程调用期间被占用，并发上升时连接池将迅速耗尽。因此每次落库各自作为短事务执行。

代价是不保证跨步骤的原子性，此处予以接受。

### 5. 两类异常共用一套降级话术

模型调用失败（`RestClientException`）与检索阶段数据异常（余弦计算抛出异常）返回同一句可读提示，不向调用方暴露堆栈。调用方只需获知服务异常；具体是模型不可用还是库内数据损坏，通过 `error` 级别日志区分。

### 6. 模型厂商切换隔离在配置层

`OllamaClientConfig` 与 `OllamaProperties` 是唯一感知下游服务类型的地方，Service 层仅依赖注入的 `RestClient`。若需将 Ollama 替换为云端 API，修改这两个类即可。

## 测试

```bash
mvn test
```

37 个用例，不依赖 MySQL 与 Ollama：

| 类 | 条数 | 覆盖内容 |
|---|---|---|
| `CosineSimilarityTest` | 8 | 同向 / 正交 / 反向 / 零向量 / 维度不一致 / null / 空 等边界 |
| `TextNormalizerTest` | 6 | 全半角转换、空白折叠、大小写归一 |
| `TextSplitterTest` | 3 | 空串、2000 字中文的切分长度与首尾对齐、非法参数 |
| `KnowledgeIngestServiceTest` | 5 | Mockito 捕获入库实体，断言字段与条数 |
| `KnowledgeRetrieveServiceTest` | 5 | 排序、Top-K 截断、不执行阈值过滤 |
| `OllamaChatClientTest` | 2 | `MockRestServiceServer` 固定 `stream:false` 与取值路径 |
| `KnowledgeChunkRepositoryTest` | 3 | `@DataJpaTest` + 嵌入式数据库 |
| `RagQaServiceTest` | 5 | 命中与未命中的提示词断言、阈值边界、降级、参数校验 |

其中两点值得说明：

期望值可推导时不应手写常量。`1 / Math.sqrt(2)` 优于 `0.7071`，手抄浮点常量易漏位，由此导致的测试失败会误导排查方向，看似余弦计算有误，实为常量抄写错误。

当两条分支都会触发同一调用时，断言目标应从「是否调用」转移到「传入了什么」。`RagQaService` 的命中与未命中均调用模型，`verify(chatClient, never())` 对两条分支返回相同结果，已失去区分能力；改用 `ArgumentCaptor` 捕获 system prompt 后做 `assertEquals` 才能验证分支选择。

## 已知边界

1. **阈值硬编码** —— 应外移到配置项，并构建小规模评测集（数十条问题 + 人工标注）反推最优值
2. **单轮问答，无上下文** —— 将历史问答按时间升序拼入 messages 数组即可支持追问
3. **全量线性扫描** —— 数百条知识块下性能充足；上千条后的演进路线为：本地缓存 → ANN 索引（HNSW）→ 十万级引入 pgvector 或 Milvus
4. **无流式输出** —— 当前需等待整段生成完毕。在交互场景中用户对首字延迟敏感，此时应改为 SSE 逐字返回
5. **定长窗口切分** —— 可能在句中截断。更优做法是优先按段落与句末标点切分，定长仅作为兜底

## 许可

MIT。见 [LICENSE](./LICENSE)。
