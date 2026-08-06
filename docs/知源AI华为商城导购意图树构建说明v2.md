# 知源 AI 华为商城导购意图树构建说明 v2（v3 修订版）

> 项目：知源 AI——面向华为商城场景的 Agentic RAG 智能导购平台  
> 修订日期：2026-08-06  
> 线上管理端：`https://yiersan218.fun/admin/intent-tree`  
> 文档用途：记录知识库与意图树优化后的目标结构、代码实现、数据边界、验证结果和上线步骤。

## 1. 修订结论

本次优化已经在本地项目和仓库内 KBpro 完成，目标结构由“6 个知识库、20 个意图节点”调整为“7 个知识库、21 个意图节点”。核心变化如下：

1. 将误放在鸿蒙兼容库中的 25 份穿戴和 13 份耳机产品档案迁入参数与卖点库。
2. 删除上述产品档案中的采集时点“在售核验”，防止静态文档被解释成当前库存事实。
3. 将“退换货与保修”“发票与售后”两个交叉知识库拆为退货退款、保修维修、发票三个单一职责知识库。
4. 将“实时权益”改名为“商城动态信息”，明确 `youcom_search` 只提供公开网页搜索线索，并非实时价格、库存或交易接口。
5. 支持稳定知识与动态页面线索双路由，例如“参数 + 当前标价”返回一个 KB 意图和一个 MCP 意图。
6. 后端开始解析 Markdown frontmatter，元数据不再进入正文切块，并可通过 `source_urls` 展示官方来源链接。
7. 新增可重复执行的知识库校验、清单生成、导入和数据库迁移脚本。

当前状态：本地代码、知识库、脚本和测试已完成；线上环境仍需部署新代码、执行 SQL 迁移并导入文档后才会变为本说明中的目标状态。

## 2. 设计原则

### 2.1 型号是检索实体，不是意图节点

`HUAWEI WATCH 5`、`Pura 90 Pro`、`MateBook 14` 等商品名、系列、型号和配置应保留在原问题和检索查询中。意图树只表达用户要解决的问题，如参数、兼容、选购、商品页搜索、价格线索或服务政策。

新增型号通常只需要更新知识库或由 MCP 搜索公开页面，不需要增加意图节点。

### 2.2 稳定知识与动态信息分层

| 信息 | 通道 | 回答边界 |
|---|---|---|
| 产品家族、稳定参数、功能卖点 | KB | 不包含当前价格、库存、优惠和动态 SKU |
| 连接、配对、投屏、系统版本、配件适配 | KB | 具体结论需对应型号和软件版本 |
| 使用、设置、迁移、清洁、重置、选购方法 | KB | 依据文档，不补造操作或规格 |
| 配送、退货退款、保修维修、发票政策 | KB | 只说明公开政策，不承诺个人个案结果 |
| 商品页、公开标价/在售、优惠、分期、以旧换新页面 | MCP | 只能称为当前搜索结果或公开网页线索 |
| 个人订单、物流、账户、支付、售后工单 | 不支持 | 需要登录或业务鉴权，不调用公开网页搜索代替 |

## 3. v3 完整意图树

```text
华为商城导购 DOMAIN [KB, hwmall]
├─ 选购决策 CATEGORY [KB, hwmall-buying]
│  ├─ 场景化推荐 TOPIC [KB, kb-buying-decision]
│  │  └─ hwmallguide + hwmallproduct
│  ├─ 商品页面搜索 TOPIC [MCP, mcp-vmall-search]
│  │  └─ youcom_search, search_type=product_search
│  └─ 商品对比 TOPIC [KB, kb-product-comparison]
│     └─ hwmallguide + hwmallproduct
├─ 商品知识 CATEGORY [KB, hwmall-product-knowledge]
│  ├─ 参数与卖点 TOPIC [KB, kb-product-parameters]
│  │  └─ hwmallproduct
│  ├─ 鸿蒙生态兼容 TOPIC [KB, kb-harmony-compatibility]
│  │  └─ hwmallcompat
│  └─ 使用与选购指南 TOPIC [KB, kb-usage-guide]
│     └─ hwmallguide
├─ 商城动态信息 CATEGORY [MCP, hwmall-current-info]
│  ├─ 价格与在售信息线索 TOPIC [MCP, mcp-price-stock]
│  │  └─ youcom_search, search_type=price_stock
│  ├─ 商城优惠页面 TOPIC [MCP, mcp-promotion]
│  │  └─ youcom_search, search_type=promotion
│  └─ 分期与以旧换新页面 TOPIC [MCP, mcp-installment-tradein]
│     └─ youcom_search, search_type=installment_tradein
├─ 服务政策 CATEGORY [KB, hwmall-service-policy]
│  ├─ 配送与安装 TOPIC [KB, kb-delivery-installation]
│  │  └─ hwmalldelivery
│  ├─ 退货换货与退款 TOPIC [KB, kb-return-refund]
│  │  └─ hwmallreturn
│  ├─ 保修与维修 TOPIC [KB, kb-warranty-repair]
│  │  └─ hwmallwarranty
│  └─ 发票 TOPIC [KB, kb-invoice]
│     └─ hwmallinvoice
└─ 系统交互 CATEGORY [SYSTEM, hwmall-system]
   ├─ 欢迎与能力介绍 TOPIC [SYSTEM, sys-welcome-capabilities]
   └─ 服务边界 TOPIC [SYSTEM, sys-service-boundary]
```

节点统计：1 个 DOMAIN、5 个 CATEGORY、15 个 TOPIC，共 21 个节点；叶子节点包含 9 个 KB、4 个 MCP 和 2 个 SYSTEM。

## 4. 知识库划分与数据量

| 目录 | Collection | 文档数 | 主要职责 |
|---|---|---:|---|
| `01_参数与卖点_KB` | `hwmallproduct` | 180 | 13 类硬件产品档案、稳定参数、卖点及档案索引 |
| `02_鸿蒙生态兼容_KB` | `hwmallcompat` | 22 | 通用兼容规则、连接协议和官方兼容资料 |
| `03_使用与选购指南_KB` | `hwmallguide` | 55 | 使用、设置、保养、选购框架、推荐和对比参考 |
| `04_配送与安装_KB` | `hwmalldelivery` | 10 | 配送、签收、安装和上门服务 |
| `05_退货换货与退款_KB` | `hwmallreturn` | 3 | 退换货政策、退款方式和办理清单 |
| `06_保修与维修_KB` | `hwmallwarranty` | 11 | 保修、维修、寄修、服务权益和三包凭证 |
| `07_发票_KB` | `hwmallinvoice` | 2 | 发票说明和开具/换开清单 |
| 合计 | — | **283** | 产品档案 **167** 份 |

### 4.1 穿戴和耳机档案修正

原 `02_鸿蒙生态兼容_KB/穿戴` 和 `02_鸿蒙生态兼容_KB/耳机` 中的 38 份文件实际是型号级产品档案。现已迁入 `01_参数与卖点_KB`，并完成以下修正：

- `intent_node` 改为“参数与卖点 KB”；
- `doc_id` 从临时 `kbplus-*` 改为正式 `product-wearable-*` 或 `product-earphone-*`；
- 标题统一为“产品档案”；
- 删除采集时点“有明确价格且未缺货”等静态在售结论；
- 保留型号相关的搭配与使用提示，但价格、库存、优惠、套餐和分期仍交给 MCP。
- 两份穿戴/耳机档案目录也迁入参数库，正文中的旧 `kbplus-*` 引用全部替换为正式 `product-*` 编码。

## 5. 混合意图路由

分类器默认只返回一个主意图；同一问题同时包含稳定知识和商城动态信息需求时，必须返回两个节点，最多两个。

| 用户问题 | 意图结果 |
|---|---|
| Mate 80 的主要参数和当前页面标价 | `kb-product-parameters` + `mcp-price-stock` |
| 预算 5000 元，推荐现在页面上还能找到的拍照手机 | `kb-buying-decision` + `mcp-vmall-search` |
| 比较 Pura 90 和 Mate 80，并看看当前优惠 | `kb-product-comparison` + `mcp-promotion` |
| FreeBuds Pro 5 怎么连接电脑，现在有什么优惠 | `kb-harmony-compatibility` + `mcp-promotion` |

混合回答时，稳定参数与规则优先使用 KB；价格、在售、优惠、分期和以旧换新只能转述 MCP 返回的可见搜索摘要，并标注索引延迟和最终核验边界。

## 6. Markdown frontmatter 与来源追溯

后端新增 frontmatter 解析链路，识别以下文档级字段：

```yaml
---
doc_id: "product-watch-5"
title: "HUAWEI WATCH 5 产品档案"
domain: "华为商城导购"
intent_node: "参数与卖点 KB"
scope: ["穿戴", "华为硬件", "产品家族档案"]
source_type: "official_search_listing_plus_support"
source_urls: ["https://www.vmall.com/...", "https://consumer.huawei.com/..."]
collected_at: "2026-08-02"
dynamic_product_data_via_mcp: true
---
```

处理规则：

1. frontmatter 在 Markdown AST 解析前从正文剥离，不参与正文 embedding。
2. 元数据写入每个向量块，供过滤、审计和来源追溯使用。
3. 文档表新增 `metadata JSONB` 保存文档级元数据。
4. 来源面板优先使用 `canonical_url`，否则使用 `source_urls` 第一项，再回退到 URL 类型文档的 `source_location`。
5. 无结束分隔符的异常 frontmatter 按普通 Markdown 处理，避免误删正文。

## 7. 分块配置

Markdown 默认采用 `structure_aware`：

```json
{
  "targetChars": 1200,
  "maxChars": 1600,
  "minChars": 400,
  "overlapChars": 150
}
```

管理端上传默认策略已同步调整。知识库发布配置位于 `resources/docs/knowledge/_meta/kb-import-config.json`。

## 8. 代码与迁移文件

- 默认意图树：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/IntentTreeFactory.java`
- 意图分类提示词：`bootstrap/src/main/resources/prompt/intent-classifier.st`
- frontmatter 解析器：`bootstrap/src/main/java/com/nageoffer/ai/ragent/core/parser/MarkdownFrontMatterParser.java`
- 文档元数据迁移：`resources/database/upgrades/v1.1.0/260806_knowledge_document_metadata.sql`
- 华为商城 7 库/21 节点迁移：`resources/database/upgrades/v1.1.0/260806_hwmall_kb_v3.sql`
- 元数据构建与校验：`scripts/kb/Build-KnowledgeMetadata.ps1`
- 幂等导入脚本：`scripts/kb/Import-KnowledgeBase.ps1`
- 意图评测集：`resources/docs/knowledge/_meta/intent-eval.jsonl`

导入脚本默认只做 dry-run，不保存账号密码；加 `-Execute` 才上传，加 `-StartChunk` 才启动异步向量化。脚本会按文件名跳过已经存在的文档。

## 9. 验证结果

本地验证结果：

- 知识库校验：283 份文档，167 份产品档案，42 条意图评测样例；缺失字段、重复 `doc_id`、目录与意图不一致、`kbplus-*`、静态价格库存断言均为 0。
- Java 编译：通过。当前机器使用 JDK 24，需显式传入 `-Dmaven.compiler.proc=full` 启用 Lombok 注解处理；项目目标字节码仍为 Java 17。
- 单元测试：5 条通过，覆盖 frontmatter 类型解析、正文剥离、异常回退、正式意图树结构与 Collection 绑定，以及 `source_urls` 官方来源链接装配。
- 前端生产构建：通过。

上述验证说明本地结构和代码可构建，不代表线上知识库已导入或线上端到端问答已通过。

## 10. 上线顺序

1. 备份 PostgreSQL、当前应用版本和现有 KBpro。
2. 执行 `260806_knowledge_document_metadata.sql`。
3. 执行 `260806_hwmall_kb_v3.sql`，生成/更新 7 个 Collection 映射和 21 个意图节点。
4. 部署最新后端、MCP Server 和前端，重启服务以刷新意图缓存。
5. 运行知识库校验脚本，确认 `status=passed`。
6. 运行导入脚本 dry-run，核对七个 Collection 和文档数。
7. 使用 `-Execute -StartChunk` 导入并启动向量化；观察消息队列、分块日志和向量库状态。
8. 核对线上文档总数为 283，各 Collection 数量与第 4 节一致。
9. 用 42 条评测样例执行意图分类回归，重点检查 5 条 KB+MCP 双路由问题。
10. 抽查官方来源链接、价格/库存措辞和个人订单服务边界。

示例命令：

```powershell
# 只校验和预览导入计划
.\scripts\kb\Import-KnowledgeBase.ps1 -BaseUrl "https://yiersan218.fun"

# 确认数据库迁移和新代码已部署后再执行
$credential = Get-Credential
.\scripts\kb\Import-KnowledgeBase.ps1 `
  -BaseUrl "https://yiersan218.fun" `
  -Credential $credential `
  -Execute `
  -StartChunk
```

## 11. 回滚与注意事项

- SQL 迁移应在数据库备份后执行；意图迁移保留同编码节点的数据库主键，移除的旧主题采用逻辑删除。
- `hwmallaftersales` 仅在其没有文档且新发票库不存在时复用为 `hwmallinvoice`，避免自动改名已有数据容器。
- 如果线上仍运行旧后端，不要先导入带 frontmatter 的文档；旧解析器会把 YAML 当正文向量化。
- 不要把 `_meta`、根 `README.md` 或旧版 KB 目录混合导入。
- `youcom_search` 无论返回何种标题或摘要，都不能被描述为已查询商城实时库存、个人订单或最终结算权益。

## 12. 最终结论

优化后的设计职责清晰：产品档案归参数与卖点，通用兼容规则归鸿蒙兼容，服务政策按真实办理主题拆分，动态商城信息统一作为公开网页线索处理。意图树规模与 SKU 数量解耦，并通过 frontmatter、来源链接、质量校验、评测集和幂等导入脚本形成可维护的发布链路。
