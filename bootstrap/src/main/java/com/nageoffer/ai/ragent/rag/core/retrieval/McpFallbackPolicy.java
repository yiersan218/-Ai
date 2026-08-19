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

package com.nageoffer.ai.ragent.rag.core.retrieval;

import cn.hutool.core.collection.CollUtil;
import com.nageoffer.ai.ragent.rag.config.McpFallbackProperties;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import com.nageoffer.ai.ragent.rag.enums.IntentLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 根据 KB 意图生成仅供运行时使用的 MCP 回退调用，不把回退节点加入分类意图树。 */
@Component
@RequiredArgsConstructor
public class McpFallbackPolicy {

    private static final String TENCENT_SEARCH = "tencent_search";

    private final McpFallbackProperties properties;

    public List<NodeScore> buildFallbackIntents(List<NodeScore> kbIntents) {
        if (!properties.isEnabled() || CollUtil.isEmpty(kbIntents)) {
            return List.of();
        }

        Map<String, NodeScore> bySearchType = new LinkedHashMap<>();
        for (NodeScore kbIntent : kbIntents) {
            if (kbIntent == null || kbIntent.getNode() == null) {
                continue;
            }
            String kbIntentId = kbIntent.getNode().getId();
            String searchType = properties.getIntentMappings().get(kbIntentId);
            if (searchType == null || searchType.isBlank()) {
                continue;
            }
            bySearchType.putIfAbsent(searchType, new NodeScore(
                    fallbackNode(kbIntent.getNode(), searchType),
                    kbIntent.getScore()
            ));
        }
        return List.copyOf(bySearchType.values());
    }

    private IntentNode fallbackNode(IntentNode kbNode, String searchType) {
        return IntentNode.builder()
                .id("fallback-" + kbNode.getId() + "-" + searchType)
                .name(kbNode.getName() + "公开页面补充")
                .description("知识库未提供足够证据时，检索华为官方公开页面补充信息。")
                .level(IntentLevel.TOPIC)
                .kind(IntentKind.MCP)
                .mcpToolId(TENCENT_SEARCH)
                .paramPromptTemplate("保留用户给出的商品名、型号、配置、地区和时间条件，只输出 JSON 参数；固定 search_type="
                        + searchType + "。不得编造 prdId、sbomCode、价格、库存、优惠或个人资格。")
                .promptSnippet("这是知识库证据不足后的官方公开页面补充；搜索摘要可能存在索引延迟，不得表述为实时业务数据。")
                .build();
    }
}
