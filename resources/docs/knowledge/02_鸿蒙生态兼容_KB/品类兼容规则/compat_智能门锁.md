---
doc_id: "compat-category-智能门锁"
title: "智能门锁生态兼容判断规则"
domain: "华为商城导购"
intent_node: "鸿蒙生态兼容 KB"
scope: ["智能门锁", "兼容规则", "华为硬件"]
source_type: "official_vmall_catalog_and_huawei_support_summary"
source_urls: ["https://www.vmall.com/portal/search/index.html?targetRoute=searchresult&searchWord=%25E6%2599%25BA%25E8%2583%25BD%25E9%2597%25A8%25E9%2594%2581&searchHistoryShow=true&searchResultPageProdShow=true", "https://consumer.huawei.com/cn/support/"]
collected_at: "2026-08-04"
dynamic_product_data_via_mcp: true
---
# 智能门锁生态兼容判断规则

## 判断顺序

1. 识别目标产品的准确家族、prdId 和 sbomCode。
2. 识别与其连接或搭配的设备型号、系统版本、接口、协议与账号区域。
3. 从 KB 获取稳定的兼容原则，再由 MCP 核对当前商品配置与官方支持信息。
4. 将“可以连接”“可以使用基础功能”“支持完整生态能力”分开表述。

## 本品类关键规则

- 下单前必须完成门体测量与安装条件确认
- NFC 卡、电池和装饰锁需按型号匹配
- 远程能力依赖网络、账号和 App 权限
- 应保留机械钥匙并设置应急开锁方案

## 追问清单

- 门体材质和厚度
- 锁体与开门方向
- 人脸、掌静脉或指纹需求
- 猫眼和远程对讲需求
- 安装区域与防水条件

## 回答边界

系统升级、功能灰度、地区、网络环境和配件版本都可能改变实际体验。没有对应型号和版本证据时，只能给出核对方法，不应直接保证兼容。
