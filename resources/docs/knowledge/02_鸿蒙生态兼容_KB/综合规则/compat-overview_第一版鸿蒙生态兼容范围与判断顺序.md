---
doc_id: "compat-overview"
title: "第一版鸿蒙生态兼容范围与判断顺序"
domain: "华为商城导购"
intent_node: "鸿蒙生态兼容 KB"
scope: ["手机", "平板", "笔记本", "穿戴（兼容）", "耳机（兼容）"]
source_type: "normalized_rules_from_official_support_pages"
source_urls: ["https://consumer.huawei.com/cn/support/content/zh-cn00737675/", "https://consumer.huawei.com/cn/support/content/zh-cn15868501/", "https://consumer.huawei.com/cn/support/content/zh-cn15880996/", "https://consumer.huawei.com/cn/support/content/zh-cn15905593/", "https://consumer.huawei.com/cn/support/content/zh-cn15907440/", "https://consumer.huawei.com/cn/support/content/zh-cn15923138", "https://consumer.huawei.com/cn/support/content/zh-cn15941521/", "https://consumer.huawei.com/cn/support/content/zh-cn15988997/", "https://consumer.huawei.com/cn/support/content/zh-cn16008856/", "https://consumer.huawei.com/cn/support/content/zh-cn16101643/", "https://consumer.huawei.com/cn/support/huaweishareonehop/"]
collected_at: "2026-08-02"
dynamic_price_stock_excluded: true
---

# 第一版鸿蒙生态兼容范围与判断顺序

## 第一版范围

核心商品为手机、平板和笔记本；穿戴与耳机只作为兼容和搭配对象，不提供完整独立导购。

## 判断顺序

1. 识别发起设备和目标设备的准确型号。
2. 核对操作系统、华为电脑管家、运动健康或智慧音频应用版本。
3. 核对是否要求相同华为帐号，以及 WLAN、蓝牙、NFC、华为分享等开关。
4. 查询对应官方支持机型清单，不能仅凭“同为华为设备”推断兼容。
5. 若问题涉及实时商品可购状态，再调用商品搜索或价格库存 MCP。
