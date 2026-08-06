---
doc_id: "compat-category-路由"
title: "路由生态兼容判断规则"
domain: "华为商城导购"
intent_node: "鸿蒙生态兼容 KB"
scope: ["路由", "兼容规则", "华为硬件"]
source_type: "official_vmall_catalog_and_huawei_support_summary"
source_urls: ["https://www.vmall.com/portal/search/index.html?targetRoute=searchresult&searchWord=%25E8%25B7%25AF%25E7%2594%25B1&searchHistoryShow=true&searchResultPageProdShow=true", "https://consumer.huawei.com/cn/support/"]
collected_at: "2026-08-04"
dynamic_product_data_via_mcp: true
---
# 路由生态兼容判断规则

## 判断顺序

1. 识别目标产品的准确家族、prdId 和 sbomCode。
2. 识别与其连接或搭配的设备型号、系统版本、接口、协议与账号区域。
3. 从 KB 获取稳定的兼容原则，再由 MCP 核对当前商品配置与官方支持信息。
4. 将“可以连接”“可以使用基础功能”“支持完整生态能力”分开表述。

## 本品类关键规则

- Mesh、子母路由和子路由必须确认同系列支持关系
- PLC 组网受电表、相位和用电环境影响
- Wi-Fi 7 能力需要终端共同支持
- 移动路由和随行 WiFi 还需确认运营商网络与 SIM 规则

## 追问清单

- 户型面积与墙体数量
- 宽带速率
- 有无网线或电力线回程
- 终端数量与 Wi-Fi 代际
- 儿童管理、手游加速和星闪网关需求

## 回答边界

系统升级、功能灰度、地区、网络环境和配件版本都可能改变实际体验。没有对应型号和版本证据时，只能给出核对方法，不应直接保证兼容。
