# Trade Agent 模块

`backend/trade-agent/` 用于存放规划中的 Java Agent 能力模块。每个子目录负责
一个完整的 Agent 工作流，现有 Spring Boot 后端负责提供可运行应用和共享
业务能力。

当前规划的模块：

```text
trade-agent/
└── trade-scanner/    事件发现、验证、评分和发布
```

`trade-agent/trade-scanner` 已加入 Maven Reactor，由 `trade-app` 组装。
持久化插件流水线、采集与 Outbox 随后端始终启动，无需设置启用开关。
已实现范围和 API 参见 [Scanner README](trade-scanner/README.md)。

默认使用 Java。Python 仅用于 `akshare_adapter/`，以及将来明确依赖 Python
专用模型库的工具。
