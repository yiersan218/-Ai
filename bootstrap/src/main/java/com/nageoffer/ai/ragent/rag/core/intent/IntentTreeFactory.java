/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.core.intent;

import com.nageoffer.ai.ragent.rag.enums.IntentKind;

import java.util.List;

import static com.nageoffer.ai.ragent.rag.enums.IntentLevel.CATEGORY;
import static com.nageoffer.ai.ragent.rag.enums.IntentLevel.DOMAIN;
import static com.nageoffer.ai.ragent.rag.enums.IntentLevel.TOPIC;

/**
 * 华为商城导购默认意图树。
 * <p>
 * 型号和 SKU 是检索实体，不是意图节点；稳定知识进入 KB，商城动态公开页面进入 MCP。
 */
public final class IntentTreeFactory {

    private static final String TENCENT_SEARCH = "tencent_search";
    private static final String PRODUCT_SNAPSHOT_RULE =
            "优先使用知识库明确资料回答；官网配置、颜色和页面标价快照仅在同一商品/SKU且采集时间、地区和有效期明确时转述；"
                    + "实时库存、最终结算价和当前优惠证据不足时再回退 MCP；不得回答个人业务数据。";

    private IntentTreeFactory() {
    }

    public static List<IntentNode> buildIntentTree() {
        IntentNode root = IntentNode.builder()
                .id("hwmall")
                .name("华为商城导购")
                .level(DOMAIN)
                .kind(IntentKind.KB)
                .description("覆盖稳定商品知识、选购决策、鸿蒙兼容、使用指南、商城公开页面搜索和服务政策；不处理个人订单、账户、支付或售后工单。")
                .build();

        IntentNode buying = category("hwmall-buying", "选购决策", root, IntentKind.KB,
                "预算与场景推荐、商品稳定信息对比，以及华为商城公开商品页查找。");
        IntentNode recommendation = kbTopic("kb-buying-decision", "场景化推荐", buying,
                "根据预算、用途、偏好和生态搭配给出候选方向。",
                List.of("预算 5000 元推荐拍照好的华为手机", "办公学习适合买哪款 MateBook"),
                List.of("hwmallguide", "hwmallproduct"), PRODUCT_SNAPSHOT_RULE);
        IntentNode productSearch = mcpTopic("mcp-vmall-search", "商品页面搜索", buying,
                "查找公开的华为商城商品页、商品列表或在售入口；搜索摘要存在索引延迟。",
                List.of("帮我找 Sound X5 的华为商城页面"),
                "公开网页搜索线索，不是实时业务数据。",
                "保留商品名、型号、配置和限制条件，只输出 JSON；固定 search_type=product_search，不得编造商品标识或交易事实。");
        IntentNode comparison = kbTopic("kb-product-comparison", "商品对比", buying,
                "比较多款硬件的稳定参数、卖点、场景差异和选购取舍。",
                List.of("Mate 80 和 Pura 90 Pro 怎么选"),
                List.of("hwmallguide", "hwmallproduct"), PRODUCT_SNAPSHOT_RULE);
        buying.setChildren(List.of(recommendation, productSearch, comparison));

        IntentNode productKnowledge = category("hwmall-product-knowledge", "商品知识", root, IntentKind.KB,
                "稳定参数与卖点、鸿蒙生态兼容，以及使用和通用品类选购知识。");
        IntentNode parameters = kbTopic("kb-product-parameters", "参数与卖点", productKnowledge,
                "13 类华为硬件的产品家族、稳定规格、功能和核心卖点，以及带采集时间的官网 SKU 配置、颜色和页面标价快照；不提供实时库存、最终结算价或个人优惠。",
                List.of("HUAWEI WATCH 5 有哪些卖点", "Pura 90 Pro 的影像参数是什么",
                        "Pura 90 Pro 有哪些颜色和配置", "Pura 90 Pro 现在页面标价多少"),
                List.of("hwmallproduct"), PRODUCT_SNAPSHOT_RULE);
        IntentNode compatibility = kbTopic("kb-harmony-compatibility", "鸿蒙生态兼容", productKnowledge,
                "连接、配对、投屏、协议、系统版本、超级终端、华为分享和配件适配规则。",
                List.of("Sound X5 怎么连接手机", "FreeBuds 能同时连接哪些设备"),
                List.of("hwmallcompat"), "具体兼容结果需对应型号、系统和软件版本。");
        IntentNode usage = kbTopic("kb-usage-guide", "使用与选购指南", productKnowledge,
                "上手、设置、迁移、保养、清洁、重置、配件使用和通用品类选购方法。",
                List.of("华为路由器怎么重置"),
                List.of("hwmallguide"), "仅使用稳定知识回答。");
        productKnowledge.setChildren(List.of(parameters, compatibility, usage));

        IntentNode dynamic = category("hwmall-current-info", "商城动态信息", root, IntentKind.MCP,
                "公开网页中的价格、在售、优惠、分期和以旧换新线索；不是商城实时业务接口。");
        dynamic.setPromptSnippet("所有结果必须标注公开网页搜索和时效边界。");
        IntentNode priceStock = mcpTopic("mcp-price-stock", "价格与在售信息线索", dynamic,
                "检索公开页面中的当前标价和在售状态线索，不保证实时价格、地区库存或结算价。",
                List.of("Mate 80 Pro 现在页面标价多少"),
                "不得把搜索摘要描述为实时库存。",
                "保留型号、配置、地区和商品标识，只输出 JSON；固定 search_type=price_stock，不得编造价格或库存。");
        IntentNode promotion = mcpTopic("mcp-promotion", "商城优惠页面", dynamic,
                "查找当前公开活动页、赠品、优惠券和活动时间说明。",
                List.of("Pura 系列现在有什么优惠页面"),
                "活动线索必须保留页面时效边界。",
                "保留商品、品类、活动名和时间条件，只输出 JSON；固定 search_type=promotion，不得编造优惠。");
        IntentNode installment = mcpTopic("mcp-installment-tradein", "分期与以旧换新页面", dynamic,
                "查找公开分期说明、免息页面和以旧换新入口；不估算个人回收价或资格。",
                List.of("MateBook 14 有几期免息页面"),
                "不得承诺个人资格或回收价。",
                "保留新旧商品和分期条件，只输出 JSON；固定 search_type=installment_tradein，不得编造个人资格。");
        dynamic.setChildren(List.of(priceStock, promotion, installment));

        IntentNode service = category("hwmall-service-policy", "服务政策", root, IntentKind.KB,
                "配送安装、退货换货退款、保修维修和发票等公开政策；不处理个人工单。") ;
        IntentNode delivery = kbTopic("kb-delivery-installation", "配送与安装", service,
                "配送范围、公开时效说明、签收、安装和上门服务边界。",
                List.of("智慧屏支持上门安装吗"),
                List.of("hwmalldelivery"), "个人订单物流不属于本节点。");
        IntentNode returns = kbTopic("kb-return-refund", "退货换货与退款", service,
                "七天无理由、质量问题、自营与第三方商品退换货、极速退款、申请材料、退货运费、退款方式与周期，以及积分、优惠和发票相关权益返还规则。",
                List.of("华为商城退货需要什么条件", "七天无理由退货拆封后还能退吗",
                        "第三方商品退货运费谁承担", "极速退款为什么少于实付金额",
                        "退款后积分和优惠券会退回吗"),
                List.of("hwmallreturn"),
                "只说明公开政策并区分自营、第三方、质量问题与无理由退货，不承诺个案审核或到账结果。");
        IntentNode warranty = kbTopic("kb-warranty-repair", "保修与维修", service,
                "保修期、凭证、非保情形、寄修维修、电子三包凭证和服务权益。",
                List.of("手机保修需要哪些凭证"),
                List.of("hwmallwarranty"), "只说明公开政策，不处理个人工单。");
        IntentNode invoice = kbTopic("kb-invoice", "发票", service,
                "华为商城发票开具与下载、抬头修改、180 天内换开、电子发票法律效力与真伪查验、报销及设备售后凭证规则。",
                List.of("电子发票如何开具", "发票信息填错了如何换开", "电子发票能用于报销吗",
                        "如何查验华为商城发票真伪", "发票丢了还能办理设备保修吗"),
                List.of("hwmallinvoice"),
                "只说明公开开票、换开、查验和售后凭证规则，不代替税务、财务或个案审核。");
        service.setChildren(List.of(delivery, returns, warranty, invoice));

        IntentNode system = category("hwmall-system", "系统交互", root, IntentKind.SYSTEM,
                "问候、能力介绍和服务边界说明。");
        IntentNode welcome = systemTopic("sys-welcome-capabilities", "欢迎与能力介绍", system,
                "问候、身份和能力范围介绍。", List.of("你好", "你能帮我做什么"));
        IntentNode boundary = systemTopic("sys-service-boundary", "服务边界", system,
                "说明数据时效、支持品类以及无法执行的账户、订单、支付、物流和工单操作。",
                List.of("能查我的订单物流吗"));
        system.setChildren(List.of(welcome, boundary));

        root.setChildren(List.of(buying, productKnowledge, dynamic, service, system));
        fillFullPath(List.of(root), null);
        return List.of(root);
    }

    private static IntentNode category(String id, String name, IntentNode parent, IntentKind kind,
                                       String description) {
        return IntentNode.builder()
                .id(id).name(name).parentId(parent.getId()).level(CATEGORY).kind(kind)
                .description(description).build();
    }

    private static IntentNode kbTopic(String id, String name, IntentNode parent, String description,
                                      List<String> examples, List<String> collections, String promptSnippet) {
        return IntentNode.builder()
                .id(id).name(name).parentId(parent.getId()).level(TOPIC).kind(IntentKind.KB)
                .description(description).examples(examples).collectionNames(collections)
                .promptSnippet(promptSnippet)
                .build();
    }

    private static IntentNode mcpTopic(String id, String name, IntentNode parent, String description,
                                       List<String> examples, String promptSnippet, String paramPromptTemplate) {
        return IntentNode.builder()
                .id(id).name(name).parentId(parent.getId()).level(TOPIC).kind(IntentKind.MCP)
                .description(description).examples(examples).mcpToolId(TENCENT_SEARCH)
                .paramPromptTemplate(paramPromptTemplate)
                .promptSnippet(promptSnippet)
                .build();
    }

    private static IntentNode systemTopic(String id, String name, IntentNode parent, String description,
                                           List<String> examples) {
        return IntentNode.builder()
                .id(id).name(name).parentId(parent.getId()).level(TOPIC).kind(IntentKind.SYSTEM)
                .description(description).examples(examples).build();
    }

    private static void fillFullPath(List<IntentNode> nodes, IntentNode parent) {
        for (IntentNode node : nodes) {
            node.setFullPath(parent == null ? node.getName() : parent.getFullPath() + " > " + node.getName());
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                fillFullPath(node.getChildren(), node);
            }
        }
    }
}
