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

package com.nageoffer.ai.ragent.rag.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 知识库证据不足时的 MCP 回退策略。
 * <p>
 * 映射以 KB 叶子意图 ID 为键、{@code tencent_search.search_type} 为值。映射属于检索编排策略，
 * 不新增可被分类器直接选中的 MCP 意图节点，从而保证稳定知识始终先查知识库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.mcp-fallback")
public class McpFallbackProperties implements InitializingBean {

    private static final Set<String> ALLOWED_SEARCH_TYPES = Set.of(
            "general",
            "product_search",
            "specifications",
            "compatibility_support",
            "service_policy"
    );

    /** 是否启用 KB 未命中后的 MCP 回退。 */
    private boolean enabled = true;

    /** Rerank 相关度最低阈值；低于阈值的片段不构成有效 KB 证据。 */
    private double minRerankScore = 0.50D;

    /** 至少需要多少条达到阈值的知识片段。 */
    private int minRelevantChunks = 1;

    /** 对含英文型号的商品问题校验型号词是否出现在命中文本中。 */
    private boolean requireEntityMatch = true;

    /** 单轮对话最多允许多少次唯一 MCP 远程调用。 */
    private int maxCallsPerTurn = 2;

    /** MCP 调用全部失败后，是否允许恢复使用达到兜底分数线的 KB 片段。 */
    private McpFailureKbFallback mcpFailureKbFallback = new McpFailureKbFallback();

    /** KB 意图 ID -> tencent_search.search_type。 */
    private Map<String, String> intentMappings = new LinkedHashMap<>();

    @Override
    public void afterPropertiesSet() {
        if (minRerankScore < 0D || minRerankScore > 1D) {
            throw new IllegalStateException("rag.mcp-fallback.min-rerank-score 必须在 [0,1] 范围内");
        }
        if (minRelevantChunks <= 0) {
            throw new IllegalStateException("rag.mcp-fallback.min-relevant-chunks 必须为正数");
        }
        if (maxCallsPerTurn <= 0) {
            throw new IllegalStateException("rag.mcp-fallback.max-calls-per-turn 必须为正数");
        }
        if (mcpFailureKbFallback == null) {
            throw new IllegalStateException("rag.mcp-fallback.mcp-failure-kb-fallback 不允许为空");
        }
        if (mcpFailureKbFallback.minRerankScore < 0D
                || mcpFailureKbFallback.minRerankScore > 1D) {
            throw new IllegalStateException(
                    "rag.mcp-fallback.mcp-failure-kb-fallback.min-rerank-score 必须在 [0,1] 范围内");
        }
        if (mcpFailureKbFallback.minRerankScore > minRerankScore) {
            throw new IllegalStateException(
                    "rag.mcp-fallback.mcp-failure-kb-fallback.min-rerank-score 不得高于正常 KB 阈值");
        }
        if (mcpFailureKbFallback.topK <= 0) {
            throw new IllegalStateException("rag.mcp-fallback.mcp-failure-kb-fallback.top-k 必须为正数");
        }
        intentMappings.forEach((intentId, searchType) -> {
            if (intentId == null || intentId.isBlank()) {
                throw new IllegalStateException("rag.mcp-fallback.intent-mappings 不允许空意图 ID");
            }
            if (!ALLOWED_SEARCH_TYPES.contains(searchType)) {
                throw new IllegalStateException(
                        "rag.mcp-fallback.intent-mappings 中存在不支持的 search_type: " + searchType);
            }
        });
    }

    @Data
    public static class McpFailureKbFallback {

        /** 是否启用 MCP 全失败后的低分 KB 兜底。 */
        private boolean enabled = true;

        /** 低分 KB 兜底最低 Rerank 分数。 */
        private double minRerankScore = 0.40D;

        /** 低分 KB 兜底最多送入回答模型的片段数。 */
        private int topK = 3;
    }
}
