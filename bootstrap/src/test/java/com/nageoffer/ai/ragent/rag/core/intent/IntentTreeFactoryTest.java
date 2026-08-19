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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentTreeFactoryTest {

    @Test
    void buildsHuaweiMallTreeWithSevenKnowledgeCollections() {
        List<IntentNode> allNodes = flatten(IntentTreeFactory.buildIntentTree());

        assertEquals(21, allNodes.size());
        assertEquals(15, allNodes.stream().filter(IntentNode::isLeaf).count());
        assertEquals(List.of("hwmallproduct"), find(allNodes, "kb-product-parameters").getEffectiveCollectionNames());
        assertEquals(List.of("hwmallreturn"), find(allNodes, "kb-return-refund").getEffectiveCollectionNames());
        assertEquals(List.of("hwmallwarranty"), find(allNodes, "kb-warranty-repair").getEffectiveCollectionNames());
        assertEquals(List.of("hwmallinvoice"), find(allNodes, "kb-invoice").getEffectiveCollectionNames());
        assertEquals("商城动态信息", find(allNodes, "hwmall-current-info").getName());
        assertEquals("tencent_search", find(allNodes, "mcp-price-stock").getMcpToolId());
        for (String id : List.of("kb-buying-decision", "kb-product-comparison", "kb-product-parameters")) {
            String promptSnippet = find(allNodes, id).getPromptSnippet();
            assertTrue(promptSnippet.contains("官网配置"));
            assertTrue(promptSnippet.contains("证据不足时再回退 MCP"));
        }
    }

    @Test
    void matchesCurrentHuaweiMallDefinitions() {
        List<IntentNode> allNodes = flatten(IntentTreeFactory.buildIntentTree());

        Map.ofEntries(
                Map.entry("hwmall", "覆盖稳定商品知识、选购决策、鸿蒙兼容、使用指南、商城公开页面搜索和服务政策；不处理个人订单、账户、支付或售后工单。"),
                Map.entry("kb-buying-decision", "根据预算、用途、偏好和生态搭配给出候选方向。"),
                Map.entry("mcp-vmall-search", "查找公开的华为商城商品页、商品列表或在售入口；搜索摘要存在索引延迟。"),
                Map.entry("kb-product-comparison", "比较多款硬件的稳定参数、卖点、场景差异和选购取舍。"),
                Map.entry("kb-product-parameters", "13 类华为硬件的产品家族、稳定规格、功能和核心卖点，以及带采集时间的官网 SKU 配置、颜色和页面标价快照；不提供实时库存、最终结算价或个人优惠。"),
                Map.entry("hwmall-current-info", "公开网页中的价格、在售、优惠、分期和以旧换新线索；不是商城实时业务接口。"),
                Map.entry("mcp-price-stock", "检索公开页面中的当前标价和在售状态线索，不保证实时价格、地区库存或结算价。"),
                Map.entry("mcp-promotion", "查找当前公开活动页、赠品、优惠券和活动时间说明。"),
                Map.entry("mcp-installment-tradein", "查找公开分期说明、免息页面和以旧换新入口；不估算个人回收价或资格。"),
                Map.entry("kb-return-refund", "七天无理由、质量问题、自营与第三方商品退换货、极速退款、申请材料、退货运费、退款方式与周期，以及积分、优惠和发票相关权益返还规则。"),
                Map.entry("kb-invoice", "华为商城发票开具与下载、抬头修改、180 天内换开、电子发票法律效力与真伪查验、报销及设备售后凭证规则。")
        ).forEach((id, description) -> assertEquals(description, find(allNodes, id).getDescription(), id));

        Map.ofEntries(
                Map.entry("mcp-vmall-search", List.of("帮我找 Sound X5 的华为商城页面")),
                Map.entry("kb-product-comparison", List.of("Mate 80 和 Pura 90 Pro 怎么选")),
                Map.entry("kb-usage-guide", List.of("华为路由器怎么重置")),
                Map.entry("mcp-price-stock", List.of("Mate 80 Pro 现在页面标价多少")),
                Map.entry("mcp-promotion", List.of("Pura 系列现在有什么优惠页面")),
                Map.entry("mcp-installment-tradein", List.of("MateBook 14 有几期免息页面")),
                Map.entry("kb-delivery-installation", List.of("智慧屏支持上门安装吗")),
                Map.entry("kb-product-parameters", List.of("HUAWEI WATCH 5 有哪些卖点", "Pura 90 Pro 的影像参数是什么",
                        "Pura 90 Pro 有哪些颜色和配置", "Pura 90 Pro 现在页面标价多少")),
                Map.entry("kb-return-refund", List.of("华为商城退货需要什么条件", "七天无理由退货拆封后还能退吗",
                        "第三方商品退货运费谁承担", "极速退款为什么少于实付金额", "退款后积分和优惠券会退回吗")),
                Map.entry("kb-warranty-repair", List.of("手机保修需要哪些凭证")),
                Map.entry("kb-invoice", List.of("电子发票如何开具", "发票信息填错了如何换开", "电子发票能用于报销吗",
                        "如何查验华为商城发票真伪", "发票丢了还能办理设备保修吗")),
                Map.entry("sys-service-boundary", List.of("能查我的订单物流吗"))
        ).forEach((id, examples) -> assertEquals(examples, find(allNodes, id).getExamples(), id));

        Map.ofEntries(
                Map.entry("hwmall-current-info", "所有结果必须标注公开网页搜索和时效边界。"),
                Map.entry("mcp-vmall-search", "公开网页搜索线索，不是实时业务数据。"),
                Map.entry("kb-harmony-compatibility", "具体兼容结果需对应型号、系统和软件版本。"),
                Map.entry("kb-usage-guide", "仅使用稳定知识回答。"),
                Map.entry("mcp-price-stock", "不得把搜索摘要描述为实时库存。"),
                Map.entry("mcp-promotion", "活动线索必须保留页面时效边界。"),
                Map.entry("mcp-installment-tradein", "不得承诺个人资格或回收价。"),
                Map.entry("kb-delivery-installation", "个人订单物流不属于本节点。"),
                Map.entry("kb-return-refund", "只说明公开政策并区分自营、第三方、质量问题与无理由退货，不承诺个案审核或到账结果。"),
                Map.entry("kb-warranty-repair", "只说明公开政策，不处理个人工单。"),
                Map.entry("kb-invoice", "只说明公开开票、换开、查验和售后凭证规则，不代替税务、财务或个案审核。")
        ).forEach((id, snippet) -> assertEquals(snippet, find(allNodes, id).getPromptSnippet(), id));

        Map.ofEntries(
                Map.entry("mcp-vmall-search", "保留商品名、型号、配置和限制条件，只输出 JSON；固定 search_type=product_search，不得编造商品标识或交易事实。"),
                Map.entry("mcp-price-stock", "保留型号、配置、地区和商品标识，只输出 JSON；固定 search_type=price_stock，不得编造价格或库存。"),
                Map.entry("mcp-promotion", "保留商品、品类、活动名和时间条件，只输出 JSON；固定 search_type=promotion，不得编造优惠。"),
                Map.entry("mcp-installment-tradein", "保留新旧商品和分期条件，只输出 JSON；固定 search_type=installment_tradein，不得编造个人资格。")
        ).forEach((id, prompt) -> assertEquals(prompt, find(allNodes, id).getParamPromptTemplate(), id));
    }

    private static IntentNode find(List<IntentNode> nodes, String id) {
        IntentNode node = nodes.stream().filter(each -> id.equals(each.getId())).findFirst().orElse(null);
        assertNotNull(node, "missing node " + id);
        return node;
    }

    private static List<IntentNode> flatten(List<IntentNode> roots) {
        List<IntentNode> nodes = new ArrayList<>();
        for (IntentNode root : roots) {
            nodes.add(root);
            nodes.addAll(flatten(root.getChildren()));
        }
        return nodes;
    }
}
