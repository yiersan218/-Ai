---
doc_id: "compat-recommendation-rules"
title: "生态搭配推荐规则与回答边界"
domain: "华为商城导购"
intent_node: "鸿蒙生态兼容 KB"
scope: ["手机", "平板", "笔记本", "穿戴（兼容）", "耳机（兼容）"]
source_type: "normalized_rules_from_official_support_pages"
source_urls: ["https://consumer.huawei.com/cn/support/content/zh-cn00737675/", "https://consumer.huawei.com/cn/support/content/zh-cn15868501/", "https://consumer.huawei.com/cn/support/content/zh-cn15880996/", "https://consumer.huawei.com/cn/support/content/zh-cn15905593/", "https://consumer.huawei.com/cn/support/content/zh-cn15907440/", "https://consumer.huawei.com/cn/support/content/zh-cn15923138", "https://consumer.huawei.com/cn/support/content/zh-cn15941521/", "https://consumer.huawei.com/cn/support/content/zh-cn15988997/", "https://consumer.huawei.com/cn/support/content/zh-cn16008856/", "https://consumer.huawei.com/cn/support/content/zh-cn16101643/", "https://consumer.huawei.com/cn/support/huaweishareonehop/"]
collected_at: "2026-08-02"
dynamic_price_stock_excluded: true
---

# 生态搭配推荐规则与回答边界

## 搭配推荐规则

- 用户已有手机时，优先询问系统版本、办公/学习/运动/影音场景，再推荐平板、电脑、穿戴或耳机方向。
- 需要跨设备文件工作流时，优先核对华为分享、多屏协同和超级终端支持清单。
- 推荐手表或耳机时，只回答连接方式、兼容条件和搭配价值，不扩展到其完整参数对比。
- 不能确认支持机型时，应明确提示查询官方清单，禁止使用品牌一致性代替兼容性证据。

## 动态信息边界

搭配商品的实时售价、库存、套餐优惠和赠品不进入本 KB，应调用 MCP 查询。
