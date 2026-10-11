# Trade Scanner 架构设计

资讯采集的字段、去重、配置和运行说明见 [NEWS_COLLECTOR.md](NEWS_COLLECTOR.md)。

`trade-scanner` 是规划中的 Java 业务模块，用于发现重要的 A 股市场事件。
它负责完整的 Scanner 工作流，包括采集编排、持久化状态、事件处理、
确定性规则、可靠消息发布以及 Scanner API。

当前模块已加入 Maven Reactor，并由 `trade-app` 组装。以下“当前实现”是已落地的
V1 范围，其后的架构章节保留作为后续演进目标，不能视为全部已经实现。

## 当前实现与启动

V1 已实现 RawMessage 持久化、Spring `@Order` 插件链、关键词多事件抽取、
精确事件合并、证据关联、版本化评分、不可变事件快照、失败退避重试、事务
Outbox、RabbitMQ mandatory routing 和 publisher confirms。

自选股行情采集器读取 `trade-market` 的已有缓存，检测最近七个自然日内完成
收盘且涨跌幅绝对值至少 4% 的日线。它不额外抓取 AkShare，不把行情异常当作
已确认公告。财联社、东方财富和同花顺资讯采集器已通过内部 Adapter 接入，每轮只保存
`externalId/title/content/url/publishedAt`，明确丢弃 Adapter 的 `raw`。数据库使用
`external_id` 唯一索引去重，并用 `source_id` 区分 `cls-news`、`eastmoney-news` 和
`ths-news`。这些资讯当前只采集，保存后直接完成，不进入事件抽取、评分或 Outbox。
公告采集器、LLM、近似合并、跨来源验证、独立 Clue 工作流和 Research 消费端尚未实现。
行情和手动消息生成的事件保持 UNVERIFIED，
验证状态不作为发布门槛；当前评分规则下，这些来源最高为 55 分，仍低于 70 分发布阈值。

先启动 `depoy/docker-compose-fundament.yml` 中 PostgreSQL 和 RabbitMQ，再运行：

```bash
mvn -f backend/pom.xml -pl trade-app -am -DskipTests package
java -jar backend/trade-app/target/trade-app-0.1.0.jar
```

Scanner 随后端始终启动，Controller、采集、处理和发布任务不再依赖启用开关。
启动后按模块内 `scanner-schema.sql` 对已有数据库做增量建表，
不要求重建 Docker volume。配置 `SCANNER_OFFICIAL_SOURCES` 为逗号分隔的官方
Collector ID；仅服务端 Collector 的消息可使用该可信策略。HTTP 手动提交不会
因冒用 sourceId 获得可信等级。总分 ≥70 且有股票关系的事件自动产生 Outbox，
不要求 CONFIRMED。发布表示值得进一步研究，而不是事实已确认。评分权重和阈值
本次不调整。消费端必须根据 messageId 幂等，使用
eventId + eventVersion 获取精确快照。

资讯采集默认每 60 秒运行一次，可通过 `SCANNER_COLLECT_DELAY_MS` 调整。Java 使用
`AKSHARE_ADAPTER_BASE_URL` 调用内部 Adapter；浏览器不直接调用资讯 Adapter。

事件分类由 `scanner_event_type` 注册表统一管理，当前注册 `EARNINGS`、
`BUYBACK`、`CONTRACT`、`RISK`、`OWNERSHIP` 和 `MARKET_MOVE`。每条
`scanner_event` 必须保存注册类型的外键；未注册或被停用的类型不会落成事件。
已有事件会在增量初始化时从快照中的 `type` 回填类型列。

当前 API：

```text
POST /api/scanner/messages
GET  /api/scanner/messages/{messageId}
POST /api/scanner/messages/{messageId}/retry
GET  /api/scanner/events?limit=20
GET  /api/scanner/event-types
GET  /api/scanner/events/{eventId}?version=1
GET  /api/scanner/events/{eventId}/evidence
GET  /api/scanner/dashboard
GET  /api/scanner/collectors/status
```

手动提交示例（仅验证采集、抽取和入库）：

```bash
curl --noproxy '*' -X POST http://localhost:8001/api/scanner/messages \
  -H 'Content-Type: application/json' \
  -d '{"sourceId":"manual","externalId":"demo-1","title":"业绩预告与回购计划","content":"公司发布净利润增长预告并提出股份回购计划。","url":null,"publishedAt":"2026-10-07T07:00:00Z","symbols":["000001"]}'
curl --noproxy '*' http://localhost:8001/api/scanner/events
curl --noproxy '*' http://localhost:8001/api/scanner/dashboard
```

消息异步处理；轮询 messages/{messageId} 可以看到状态和 eventIds。重复提交返回
同一 messageId。提取异常最多重试五次，之后为 FAILED，允许手动 retry；MQ 失败
持续退避重试。数据库不可用时事务回滚，待下一轮轮询重试。原文、事件和评分用
JSON 快照保存，插件成功轨迹保存于 raw_message.trace；逐插件独立恢复和执行表
尚未实现。精确合并使用类型、股票、日期、标题和摘要，近似转载暂不合并。

`mvn -f backend/pom.xml test` 验证插件、评分和工作流写入行为，并自动启动测试专属
Embedded PostgreSQL 验证真实 Mapper、事务回滚、并发锁和 Watchlist XML 兼容性。
测试无需 Docker，不连接应用数据库；RabbitMQ Confirm 仍使用 Mock，真实 Broker
故障恢复需要另行联调。

## MyBatis-Plus 数据访问

ScannerService、CollectorJob 和 ScannerMessaging 统一调用 `ScannerRepository`，
业务类不再直接使用 JdbcTemplate 或拼接 SQL。Repository 使用 MyBatis-Plus
`BaseMapper`、`QueryWrapper` 和 `UpdateWrapper` 实现普通查询、状态更新和统计。

```text
ScannerService / CollectorJob / ScannerMessaging
                       |
                       v
                ScannerRepository
                       |
                       v
      BaseMapper + 自定义 PostgreSQL Mapper SQL
                       |
                       v
                  PostgreSQL
```

持久化实体与对外 JSON 契约分开，避免修改 API 字段。`UuidTypeHandler` 负责原生
UUID 参数和结果，时间字段使用 OffsetDateTime。NULL 更新通过 Wrapper 显式设置，
保证重试或处理成功后旧错误确实被清空。

`ON CONFLICT`、`FOR UPDATE SKIP LOCKED`、复合主键证据关系和不可变版本使用自定义
Mapper SQL，保持 PostgreSQL 原子性。事务边界仍由应用服务和 Publisher 的
TransactionTemplate 管理，事件、证据、版本和 Outbox 保持同一事务。

全后端使用 `mybatis-plus-spring-boot3-starter` 初始化共享 SqlSessionFactory，
保留 Watchlist 的现有 XML Mapper，不同时引入旧 MyBatis Starter。配置项为：

```properties
mybatis-plus.mapper-locations=classpath*:mybatis/*.xml
mybatis-plus.type-handlers-package=com.tradeagent.scanner.repository.typehandler
```

`scanner-schema.sql` 仍通过 ResourceDatabasePopulator 做启动建表，不用于业务 CRUD。
Research 的 JDBC 存储本次未迁移。MyBatis-Plus 集成规则参考
[官方安装说明](https://baomidou.com/getting-started/install/)。

## 技术边界

Scanner 默认使用 Java 实现以下能力：

- 使用 Spring 调度任务并编排工作流。
- 使用 PostgreSQL 事务和 Repository 持久化数据。
- 定义 RawMessage、Event 等领域模型。
- 编排事件去重和规范事件合并。
- 实现来源验证和重要性评分规则。
- 调用提供普通 HTTP/JSON 接口的 LLM。
- 使用事务 Outbox 向 RabbitMQ 发布消息。
- 提供公共 REST API 和运行状态。

只有依赖 Python 特有上游库或模型运行环境时才使用 Python：

- AkShare 行情、公告和新闻访问继续放在 `akshare_adapter/`。
- 基于 pandas 的数据源适配继续放在 `akshare_adapter/`。
- 将来需要本地 Embedding、NLP 或 GPU 模型，并且没有合适的 Java
  实现时，可以增加小型内部 Python 模型服务。

Python 工具只返回数据或模型输出，不管理 Scanner 工作流状态，不写入
Scanner 数据表，不调度任务，也不发布业务事件。

## 运行架构

```text
Vue Scanner Dashboard
        |
        v
Spring Boot trade-scanner
  |-- PostgreSQL：消息、事件、评分、任务记录、Outbox
  |-- RabbitMQ：向 Research 发送重要事件 ID
  |-- Redis：可选的锁、短期缓存和限流
  |-- HTTP --> akshare_adapter --> AkShare
  |-- HTTP --> DeepSeek 或其他配置的 LLM
  +-- HTTP --> 可选的 Python 专用模型服务
```

浏览器只调用 Spring Boot。Java 在修改 Scanner 业务状态前，必须验证所有
外部服务和模型返回的数据。

## Maven 依赖方向

```text
trade-app
   +--> trade-scanner --> trade-watchlist --> trade-market
   +--> trade-research ---------------------> trade-market
   +--> trade-strategy ---------------------> trade-market
```

`trade-scanner` 可以依赖 `trade-watchlist` 和 `trade-market`，以复用权威的
自选股数据和股票身份契约。它不能依赖 Research 或 Strategy。

如果 Research 将来在模块化单体内部消费 Scanner 接收的事件，可以依赖
`trade-scanner` 暴露的小型只读事件查询契约：

```text
trade-research --> trade-scanner --> trade-watchlist --> trade-market
```

## 规划目录结构

```text
backend/trade-agent/trade-scanner/
├── pom.xml
└── src/
    ├── main/java/com/tradeagent/scanner/
    │   ├── api/              Scanner REST API 和 API DTO
    │   ├── client/           AkShare、LLM 和可选模型客户端
    │   ├── collector/        数据源采集器接口和实现
    │   ├── config/           类型化配置和客户端配置
    │   ├── domain/           实体、值对象、枚举和状态转换
    │   ├── extraction/       结构化事件抽取
    │   ├── deduplication/    指纹和语义相似度匹配
    │   ├── mapping/          事件与股票关联
    │   ├── validation/       来源可信度和交叉验证规则
    │   ├── scoring/          版本化的重要性评分规则
    │   ├── pipeline/         幂等的处理阶段编排
    │   ├── repository/       PostgreSQL Repository 和 MyBatis Mapper
    │   ├── messaging/        Outbox 和 RabbitMQ Publisher
    │   ├── job/              采集、重试、Pipeline 和 Outbox 任务
    │   └── service/          应用用例和查询服务
    ├── main/resources/
    │   └── mybatis/          Scanner 自己维护的 Mapper XML
    └── test/java/com/tradeagent/scanner/
```

这些包共同组成一个完整的业务能力，不为每个技术层单独创建 Maven 模块。

## Collector 接口

Collector 获取来源数据，并把传输层响应转换为 RawMessage 草稿。Collector
不负责抽取事件、计算重要性分数或发布 MQ 消息。

规划中的 Java 接口：

```java
public interface MessageCollector {

    /** 返回该数据源采集器的稳定标识。 */
    String sourceId();

    /** 采集增量游标之后产生的消息。 */
    CollectorResult collect(CollectorCursor cursor);
}
```

第一批 Collector：

```text
WatchlistMarketCollector
AnnouncementCollector
NewsCollector
```

当前资讯实现将 NewsCollector 拆成 `ClsNewsCollector`、
`EastmoneyNewsCollector` 和 `ThsNewsCollector`。三个 Collector 共用统一 DTO，
但各自保留独立来源 ID；它们只写 RawMessage，不参与事件 Pipeline。

后续接入的社交媒体来源只能生成 `Clue`，不能直接写入正式 Event 池。

## Pipeline

```text
采集来源数据
  -> 持久化 RawMessage
  -> 抽取结构化事件候选项
  -> 匹配或创建规范 Event
  -> 关联所有支持或反驳该事件的来源
  -> 关联相关股票
  -> 应用来源验证规则
  -> 计算版本化的重要性分数
  -> 满足发布条件时写入 Outbox
  -> 通过 RabbitMQ 向 Research 发布 eventId
```

系统必须先持久化 RawMessage，再进行事件抽取，确保 LLM 或上游服务故障时
不会丢失原始证据。每个处理阶段都必须保持幂等，并且能够独立重试。

LLM 输出只作为候选结果。Java 必须验证其结构，并应用确定性规则，然后
才能保存最终验证状态、评分或发布状态。

## 领域对象

### RawMessage

不可变的来源证据：

```text
id
source_id
external_id
source_type
title
content
url
author
published_at
collected_at
content_hash
raw_payload
processing_status
retry_count
last_error
```

### Event

由一个或多个 RawMessage 形成的规范事件：

```text
id
event_type
title
summary
occurred_at
industry
action
magnitude
verification_status
importance_score
processing_status
publication_status
canonical_fingerprint
extractor_version
policy_version
created_at
updated_at
```

### EventSource

Event 与 RawMessage 之间的多对多证据关系。它记录某个来源是支持还是反驳
事件、来源权重以及主来源选择结果。

### EventStockRelation

连接 Event 与股票，并保存关系类型、相关度、证据、原因和 Mapper 版本。
规划中的关系类型：

```text
DIRECT_MENTION
INDUSTRY
SUPPLIER
CUSTOMER
COMPETITOR
WATCHLIST_MATCH
MODEL_INFERRED
```

### EventScore

保存可解释、可版本化的评分维度：

```text
credibility
impact
relevance
novelty
persistence
total
reasons
policy_version
```

### Clue

表示尚未验证的社交媒体线索。只有交叉验证结果满足确定性晋升规则后，
Clue 才能转换为 Event。

V1 阶段的 `OpportunityCandidate` 是一个查询投影：经过验证、分数较高并且
已经关联股票的 Event。V1 暂时不需要为它创建独立数据表。

## 状态模型

处理进度、事实验证和消息发布分别使用独立状态：

```text
processing_status：
  COLLECTED -> EXTRACTED -> DEDUPLICATED -> MAPPED -> SCORED -> COMPLETED
  可重试错误 -> RETRY_PENDING
  终止错误   -> FAILED

verification_status：
  UNVERIFIED | MULTI_SOURCE | CONFIRMED | DISPUTED | REJECTED

publication_status：
  NOT_ELIGIBLE | READY | PUBLISHED | PUBLISH_FAILED
```

一个 Event 可以完成技术处理而仍处于未验证状态。只要达到评分阈值并关联股票，
就可以发送给 Research 进一步核实和分析；验证状态保留作为事件信息，不阻止发布。

## 规划的数据表

| 表 | 用途 |
| --- | --- |
| `scanner_source` | 稳定的数据源身份和可信等级 |
| `scanner_collector_cursor` | 增量采集检查点 |
| `scanner_collector_run` | 运行状态、时间、数量和错误 |
| `scanner_raw_message` | 不可变的来源证据和原始载荷 |
| `scanner_event_type` | 已注册事件类型、中文名称、分类版本和启用状态 |
| `scanner_event` | 规范事件和生命周期状态 |
| `scanner_event_source` | Event 与证据的关系 |
| `scanner_event_stock` | 股票关系及其解释 |
| `scanner_event_score` | 版本化的评分维度 |
| `scanner_clue` | 未验证线索的生命周期 |
| `scanner_outbox` | 事务性 RabbitMQ 消息发布 |

现有 `watchlist` 表仍然是自选股的权威数据源。Scanner 通过 Watchlist 模块
读取数据，不重复建立一套自选股存储。

建议的唯一性约束：

```text
UNIQUE (source_id, external_id)
没有 external ID 时使用 UNIQUE (source_id, content_hash)
UNIQUE (event_id, raw_message_id)
UNIQUE (event_id, symbol, relation_type)
```

## Python 工具契约

### AkShare Adapter

Java 可以调用以下内部接口：

```text
GET /internal/scanner/market/spot
GET /internal/scanner/announcements
GET /internal/scanner/news
```

实现时必须根据最新 AkShare 文档确认具体接口及其参数。Adapter 只返回来源
数据，不写入 Scanner 数据表。

### 可选模型服务

只有出现明确的 Python 专用需求时才引入 Python 模型服务，例如本地
sentence-transformers Embedding 模型或 GPU 推理。该服务应该保持小型且
无状态，例如：

```text
POST /internal/models/embeddings
```

Java 仍然负责批量策略、重试、去重决策和持久化状态。普通云端 LLM API
应该直接由 Java 调用。

## 向 Research 可靠发布事件

Event 超过配置阈值后，系统在同一个 PostgreSQL 事务中更新 Event，并插入
`scanner_outbox`。Publisher 随后发送：

```json
{
  "eventId": "3ee3caa1-0000-0000-0000-000000000000",
  "schemaVersion": 1
}
```

规划中的 RabbitMQ 拓扑：

```text
exchange:    tradeagent.event
routing key: scanner.important
queue:       research.important-event
dead letter: research.important-event.dlq
```

Research 通过小型 Java 查询契约或后端内部 API，按照 ID 读取权威事件详情。
队列消息不复制完整 Event，避免消息中的数据过期。

## 规划中的公共 API

```text
GET  /api/scanner/dashboard
GET  /api/scanner/events
GET  /api/scanner/event-types
GET  /api/scanner/events/{eventId}
GET  /api/scanner/clues
GET  /api/scanner/collectors/status
POST /api/scanner/events/{eventId}/retry
POST /api/scanner/clues/{clueId}/verify
```

以上接口目前只是架构目标，尚未实现。

## 实施顺序

1. 定义 EventType、状态枚举、数据库 Schema 和 Java 领域契约。
2. 增加 Maven 模块、Repository、空 Pipeline 和 Collector 健康状态。
3. 在不依赖 LLM 的情况下完成自选股行情异常垂直切片。
4. 增加公告和新闻 Adapter 接口，并持久化 RawMessage。
5. 增加基于 Java 的结构化事件抽取和 LLM 集成。
6. 增加去重、股票关联、验证和评分规则。
7. 增加事务 Outbox 和 Research 队列。
8. 增加社交媒体 Clue、Dashboard API 和运行监控指标。
