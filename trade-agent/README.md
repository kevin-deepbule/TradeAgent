# Trade Agent Modules

`trade-agent/` is the planned home for Java-based agent capability modules.
Each child directory owns one complete agent workflow while the existing
Spring Boot backend supplies the runnable application and shared business
capabilities.

Current planned module:

```text
trade-agent/
└── trade-scanner/    event discovery, validation, scoring, and publication
```

The directory currently contains architecture documentation only. Maven
aggregation and executable code will be added when Scanner implementation
begins.

Java is the default language. Python remains limited to `akshare_adapter/` and
future tools that have a concrete dependency on Python-only model libraries.

