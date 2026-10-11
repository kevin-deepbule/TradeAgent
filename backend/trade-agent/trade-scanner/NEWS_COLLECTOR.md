# Scanner 资讯采集说明

## 数据来源

Scanner 当前定时采集三个 AkShare 资讯来源：

| `source_id` | 来源 | Adapter 接口 |
|---|---|---|
| `cls-news` | 财联社 | `/internal/news/cls` |
| `eastmoney-news` | 东方财富 | `/internal/news/eastmoney` |
| `ths-news` | 同花顺 | `/internal/news/ths` |

数据链路：

```text
AkShare Adapter
  -> NewsAdapterClient
  -> ClsNewsCollector / EastmoneyNewsCollector / ThsNewsCollector
  -> externalId 去重
  -> scanner_raw_message
```

## 存储字段

每条资讯保存为 Scanner `RawMessage`：

```json
{
  "sourceId": "eastmoney-news",
  "externalId": "...",
  "title": "...",
  "content": "...",
  "url": "...",
  "publishedAt": "...",
  "symbols": []
}
```

Adapter 响应中的 `raw` 不在 Java DTO 中声明，因此不会保存到数据库。

`scanner_raw_message` 使用：

- `external_id` 保存上游生成的稳定消息标识；
- 非空 `external_id` 唯一索引负责去重；
- `source_id` 区分财联社、东方财富和同花顺；
- 重复 `externalId` 不覆盖首次保存的原始消息。

## 当前处理边界

这三个来源均为 ingestion-only。资讯入库后直接标记为 `COMPLETED`，并将
`trace` 保存为：

```json
["ingestion-only"]
```

当前不执行：

- 事件抽取或分类；
- 股票和行业映射；
- 重要性评分；
- Event 创建；
- Outbox 或 RabbitMQ 发布。

## 采集周期

默认每 60 秒执行一次全部 Collector。可在 `depoy/.env` 中调整：

```dotenv
SCANNER_COLLECT_DELAY_MS=60000
```

市场 Collector 和三个资讯 Collector 当前共用该周期。

## 启动

先从项目根目录启动 Adapter：

```bash
source akshare_adapter/.venv/bin/activate
python3 -m akshare_adapter.server
```

再构建并启动后端：

```bash
mvn -f backend/pom.xml -pl trade-app -am -DskipTests package
java -jar backend/trade-app/target/trade-app-0.1.0.jar
```

后端启动时会执行 `scanner-schema.sql`，为已有 PostgreSQL 数据库增量增加
`external_id` 列及唯一索引，无需删除已有 Docker volume。

## 检查采集状态

后端启动约一个采集周期后执行：

```bash
curl --noproxy '*' http://localhost:8001/api/scanner/collectors/status
```

预期能看到：

```text
cls-news
eastmoney-news
ths-news
```

## 检查数据库

```sql
SELECT
    source_id,
    external_id,
    status,
    trace,
    payload::jsonb
FROM scanner_raw_message
WHERE source_id IN ('cls-news', 'eastmoney-news', 'ths-news')
ORDER BY created_at DESC
LIMIT 30;
```

资讯的 `status` 应为 `COMPLETED`，持久化的 `payload` 中不应存在 `raw`。

## 验证命令

```bash
mvn -f backend/pom.xml -pl trade-agent/trade-scanner -am test
mvn -f backend/pom.xml -pl trade-app -am -DskipTests package
```
