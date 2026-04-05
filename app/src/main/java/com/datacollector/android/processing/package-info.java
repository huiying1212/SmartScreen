/**
 * 中层处理包 — 三层架构的核心业务逻辑层。
 *
 * <h3>三层架构概览</h3>
 * <pre>
 *   ┌─────────────────────────────────────────────────────┐
 *   │  展示层 (Presentation)                               │
 *   │  activities/ — UI 界面                               │
 *   │  services/FloatingOverlayService — 悬浮窗 UI         │
 *   │  views/ — 自定义 View                                │
 *   ├─────────────────────────────────────────────────────┤
 *   │  处理层 (Processing) ← 本包                          │
 *   │  ContextSnapshotCollector — 统一快照构建              │
 *   │  LocationContextInferrer — 位置场景推断               │
 *   │  DataPersistenceManager  — 数据持久化                 │
 *   │  LLMScoringEngine        — LLM 评分引擎              │
 *   │  MoodScoreEngine         — 多维度心情评分             │
 *   │  DataAggregator          — 时间窗口数据聚合           │
 *   │  DataSanitizer           — PII 脱敏                  │
 *   ├─────────────────────────────────────────────────────┤
 *   │  采集层 (Collection)                                 │
 *   │  collectors/ — 各类数据采集器                         │
 *   │  interfaces/ — 采集器契约                             │
 *   │  managers/DataCollectorManager — 采集器调度           │
 *   │  services/DataCollectionService — 采集服务调度        │
 *   └─────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>依赖方向</h3>
 * 展示层 → 处理层 → 采集层（单向依赖，不允许反向引用）
 */
package com.datacollector.android.processing;
