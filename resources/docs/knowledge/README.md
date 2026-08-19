# 知源 AI：华为商城 Agentic RAG 导购知识库（KBpro）

本知识库面向华为商城硬件导购，覆盖商品家族识别、稳定卖点、官网商品基础信息快照、鸿蒙生态兼容、使用与选购、配送安装、退换货退款、保修维修和发票知识。

## 产品范围

- 手机、平板、笔记本、穿戴、耳机；
- 智慧屏、路由、音箱、智能眼镜；
- 打印机、智能门锁、家庭存储；
- 充电器、移动电源、数据线、手写笔、表带、保护壳和无线充电设备等主要配件。

不包含鸿蒙智行汽车、第三方鸿蒙智选商品、食品酒饮、数字内容和服务类商品。

## 数据边界

- KB 优先：产品档案除稳定参数与卖点外，还记录带采集时间的华为商城中国区商品基础信息快照，包括 prdId、全部 sbomCode、颜色/版本/容量/尺寸等可选项、SKU 名称、页面标价、限时活动价及有效期、公开权益、评价快照和商品页入口状态。
- MCP 回退：只有知识库未收录目标型号、快照超过建议刷新日期、型号/SKU 对不上，或用户询问的实时库存、最终结算价、账号权益等信息没有足够证据时，才调用 MCP 查询。
- 快照不是交易承诺：页面标价不等于到手价或最终结算价；购买入口状态不等于地区实时库存。个人订单、物流、账号、支付和售后工单不在当前公开页面数据能力范围内。
- 时效管理：每份已补充产品档案都包含 snapshot_collected_at、snapshot_refresh_after、snapshot_region 和来源链接。超过刷新日期的时效性问题按证据不足处理，不能把旧快照表述为“当前事实”。

## Collection 映射

| 目录 | 意图节点 | Collection |
|---|---|---|
| `01_参数与卖点_KB` | 参数与卖点 | `hwmallproduct` |
| `02_鸿蒙生态兼容_KB` | 鸿蒙生态兼容 | `hwmallcompat` |
| `03_使用与选购指南_KB` | 使用与选购指南 | `hwmallguide` |
| `04_配送与安装_KB` | 配送与安装 | `hwmalldelivery` |
| `05_退货换货与退款_KB` | 退货换货与退款 | `hwmallreturn` |
| `06_保修与维修_KB` | 保修与维修 | `hwmallwarranty` |
| `07_发票_KB` | 发票 | `hwmallinvoice` |

导入时排除根目录 `README.md` 和 `_meta/**`。建议使用 Markdown/结构感知分块，目标 1200 字符、重叠 150 字符并保留标题。完整清单、来源、统计和校验结果位于 `_meta`。

商城检索快照由 `scripts/kb/Collect-VmallProductSnapshots.ps1` 生成，商品详情缓存位于 `_meta/vmall-product-detail-cache.json`，再由 `scripts/kb/Apply-VmallProductSnapshots.ps1` 幂等写入产品档案。写入后必须运行 `scripts/kb/Build-KnowledgeMetadata.ps1` 重建清单并校验。
