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

    private static final String YOUCOM_SEARCH = "youcom_search";

    private IntentTreeFactory() {
    }

    public static List<IntentNode> buildIntentTree() {
        IntentNode root = IntentNode.builder()
                .id("hwmall")
                .name("华为商城导购")
                .level(DOMAIN)
                .kind(IntentKind.KB)
                .description("覆盖华为硬件的稳定商品知识、选购决策、鸿蒙兼容、使用指南、商城公开页面搜索和服务政策；不处理个人订单、账户、支付或售后工单。")
                .build();

        IntentNode buying = category("hwmall-buying", "选购决策", root, IntentKind.KB,
                "预算与场景推荐、商品稳定信息对比，以及华为商城公开商品页查找。");
        IntentNode recommendation = kbTopic("kb-buying-decision", "场景化推荐", buying,
                "根据预算、用途、偏好和生态搭配给出候选方向；当前价格或在售信息需同时路由到 MCP。",
                List.of("预算 5000 元推荐拍照好的华为手机", "办公学习适合买哪款 MateBook"),
                List.of("hwmallguide", "hwmallproduct"));
        IntentNode productSearch = mcpTopic("mcp-vmall-search", "商品页面搜索", buying,
                "查找当前可公开检索的华为商城商品页、商品列表或在售入口；搜索摘要存在索引延迟。",
                List.of("帮我找 Sound X5 的华为商城页面", "商城里有哪些 Pura 系列商品页"),
                "product_search");
        IntentNode comparison = kbTopic("kb-product-comparison", "商品对比", buying,
                "比较两款或多款硬件的稳定参数、卖点、场景差异和选购取舍；当前价格另走 MCP。",
                List.of("Mate 80 和 Pura 90 Pro 怎么选", "比较两款华为平板的办公能力"),
                List.of("hwmallguide", "hwmallproduct"));
        buying.setChildren(List.of(recommendation, productSearch, comparison));

        IntentNode productKnowledge = category("hwmall-product-knowledge", "商品知识", root, IntentKind.KB,
                "稳定参数与卖点、鸿蒙生态兼容，以及使用和通用品类选购知识。");
        IntentNode parameters = kbTopic("kb-product-parameters", "参数与卖点", productKnowledge,
                "13 类华为硬件的产品家族、稳定规格、功能和核心卖点；排除价格、库存、优惠和动态 SKU。",
                List.of("HUAWEI WATCH 5 有哪些卖点", "Pura 90 Pro 的影像参数是什么"),
                List.of("hwmallproduct"));
        IntentNode compatibility = kbTopic("kb-harmony-compatibility", "鸿蒙生态兼容", productKnowledge,
                "连接、配对、投屏、协议、系统版本、超级终端、华为分享和配件适配规则。",
                List.of("Sound X5 怎么连接手机", "FreeBuds 能同时连接哪些设备"),
                List.of("hwmallcompat"));
        IntentNode usage = kbTopic("kb-usage-guide", "使用与选购指南", productKnowledge,
                "上手、设置、迁移、保养、清洁、重置、配件使用和通用品类选购方法。",
                List.of("华为路由器怎么重置", "智慧屏日常怎么清洁"),
                List.of("hwmallguide"));
        productKnowledge.setChildren(List.of(parameters, compatibility, usage));

        IntentNode dynamic = category("hwmall-current-info", "商城动态信息", root, IntentKind.MCP,
                "通过公开网页搜索获取价格、在售、优惠、分期和以旧换新线索；不是商城实时业务接口。");
        IntentNode priceStock = mcpTopic("mcp-price-stock", "价格与在售信息线索", dynamic,
                "检索商城公开页面中的当前标价和在售状态线索，不保证实时价格、地区库存或结算价。",
                List.of("Mate 80 Pro 现在页面标价多少", "商城页面显示这款还在售吗"),
                "price_stock");
        IntentNode promotion = mcpTopic("mcp-promotion", "商城优惠页面", dynamic,
                "查找当前公开活动页、赠品、优惠券和活动时间说明，结果需标注抓取时效。",
                List.of("Pura 系列现在有什么优惠页面", "最近有哪些华为商城活动"),
                "promotion");
        IntentNode installment = mcpTopic("mcp-installment-tradein", "分期与以旧换新页面", dynamic,
                "查找公开的分期说明、免息页面和以旧换新入口；不估算个人回收价或资格。",
                List.of("MateBook 14 有几期免息页面", "以旧换新入口在哪里"),
                "installment_tradein");
        dynamic.setChildren(List.of(priceStock, promotion, installment));

        IntentNode service = category("hwmall-service-policy", "服务政策", root, IntentKind.KB,
                "配送安装、退货换货退款、保修维修和发票等公开政策；不处理个人工单。") ;
        IntentNode delivery = kbTopic("kb-delivery-installation", "配送与安装", service,
                "配送范围、公开时效说明、签收、安装和上门服务边界。",
                List.of("智慧屏支持上门安装吗", "签收时需要检查什么"),
                List.of("hwmalldelivery"));
        IntentNode returns = kbTopic("kb-return-refund", "退货换货与退款", service,
                "退换货条件、申请材料、退款方式和一般处理周期。",
                List.of("华为商城退货需要什么条件", "退款原路返回要多久"),
                List.of("hwmallreturn"));
        IntentNode warranty = kbTopic("kb-warranty-repair", "保修与维修", service,
                "保修期、凭证、非保情形、寄修维修、电子三包凭证和服务权益。",
                List.of("手机保修需要哪些凭证", "寄修前需要准备什么"),
                List.of("hwmallwarranty"));
        IntentNode invoice = kbTopic("kb-invoice", "发票", service,
                "华为商城发票开具、换开和核对规则。",
                List.of("电子发票如何开具", "发票信息填错了怎么办"),
                List.of("hwmallinvoice"));
        service.setChildren(List.of(delivery, returns, warranty, invoice));

        IntentNode system = category("hwmall-system", "系统交互", root, IntentKind.SYSTEM,
                "问候、能力介绍和服务边界说明。");
        IntentNode welcome = systemTopic("sys-welcome-capabilities", "欢迎与能力介绍", system,
                "问候、身份和能力范围介绍。", List.of("你好", "你能帮我做什么"));
        IntentNode boundary = systemTopic("sys-service-boundary", "服务边界", system,
                "说明数据时效、支持品类以及无法执行的账户、订单、支付、物流和工单操作。",
                List.of("能查我的订单物流吗", "你能帮我提交售后工单吗"));
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
                                      List<String> examples, List<String> collections) {
        return IntentNode.builder()
                .id(id).name(name).parentId(parent.getId()).level(TOPIC).kind(IntentKind.KB)
                .description(description).examples(examples).collectionNames(collections)
                .promptSnippet("仅使用稳定知识回答；价格、库存、优惠、动态 SKU 和个人业务数据不属于本节点。")
                .build();
    }

    private static IntentNode mcpTopic(String id, String name, IntentNode parent, String description,
                                       List<String> examples, String searchType) {
        return IntentNode.builder()
                .id(id).name(name).parentId(parent.getId()).level(TOPIC).kind(IntentKind.MCP)
                .description(description).examples(examples).mcpToolId(YOUCOM_SEARCH)
                .paramPromptTemplate("保留用户给出的商品名、型号、配置、地区和时间条件，只输出 JSON 参数；固定 search_type="
                        + searchType + "。不得编造 prdId、sbomCode、价格、库存、优惠或个人资格。")
                .promptSnippet("搜索结果是公开网页线索且可能存在索引延迟，不得表述为商城实时业务数据。")
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
