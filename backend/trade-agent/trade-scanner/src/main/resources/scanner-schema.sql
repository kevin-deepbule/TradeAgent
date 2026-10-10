-- 保存原始证据、不可变事件版本、处理重试信息和事务 Outbox。
CREATE TABLE IF NOT EXISTS scanner_collector_status (
 source_id TEXT PRIMARY KEY, last_run_at TIMESTAMPTZ NOT NULL, message_count INTEGER NOT NULL, last_error TEXT
);
CREATE TABLE IF NOT EXISTS scanner_raw_message (
 id UUID PRIMARY KEY, source_id TEXT NOT NULL, dedup_key TEXT NOT NULL, payload TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'COLLECTED', attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_error TEXT, trace TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(source_id, dedup_key)
);
ALTER TABLE scanner_raw_message ADD COLUMN IF NOT EXISTS collector_origin BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS scanner_raw_pending ON scanner_raw_message(status, next_attempt_at);
CREATE TABLE IF NOT EXISTS scanner_event_type (
 code TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL,
 taxonomy_version TEXT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT true,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO scanner_event_type(code,name,description,taxonomy_version) VALUES
 ('EARNINGS','业绩事件','业绩预告、业绩快报或净利润变化','scanner-taxonomy:v1'),
 ('BUYBACK','股份回购','公司股份回购计划或进展','scanner-taxonomy:v1'),
 ('CONTRACT','合同中标','重大合同签订或项目中标','scanner-taxonomy:v1'),
 ('RISK','风险事件','立案、处罚、退市等风险事项','scanner-taxonomy:v1'),
 ('OWNERSHIP','股权变动','控制人变更或股权转让','scanner-taxonomy:v1'),
 ('MARKET_MOVE','行情异动','价格或成交量的异常变化','scanner-taxonomy:v1')
ON CONFLICT(code) DO UPDATE SET name=excluded.name,description=excluded.description,
 taxonomy_version=excluded.taxonomy_version,updated_at=now();
CREATE TABLE IF NOT EXISTS scanner_event (
 id UUID PRIMARY KEY, fingerprint TEXT NOT NULL UNIQUE, payload TEXT NOT NULL,
 event_type TEXT REFERENCES scanner_event_type(code),
 version BIGINT NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE scanner_event ADD COLUMN IF NOT EXISTS event_type TEXT REFERENCES scanner_event_type(code);
UPDATE scanner_event event
SET event_type = event.payload::jsonb ->> 'type'
FROM scanner_event_type type
WHERE event.event_type IS NULL AND type.code = event.payload::jsonb ->> 'type';
ALTER TABLE scanner_event ALTER COLUMN event_type SET NOT NULL;
CREATE INDEX IF NOT EXISTS scanner_event_type_updated ON scanner_event(event_type,updated_at DESC);
CREATE TABLE IF NOT EXISTS scanner_event_revision (
 event_id UUID NOT NULL REFERENCES scanner_event(id), version BIGINT NOT NULL,
 payload TEXT NOT NULL, PRIMARY KEY(event_id, version)
);
CREATE TABLE IF NOT EXISTS scanner_event_source (
 event_id UUID NOT NULL REFERENCES scanner_event(id), raw_message_id UUID NOT NULL REFERENCES scanner_raw_message(id),
 PRIMARY KEY(event_id, raw_message_id)
);
CREATE TABLE IF NOT EXISTS scanner_outbox (
 id UUID PRIMARY KEY, event_id UUID NOT NULL REFERENCES scanner_event(id), event_version BIGINT NOT NULL,
 payload TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_error TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(event_id, event_version)
);
CREATE INDEX IF NOT EXISTS scanner_outbox_pending ON scanner_outbox(status, next_attempt_at);
