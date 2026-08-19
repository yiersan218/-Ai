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
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.McpFallbackProperties;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.dto.KbResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 判断知识库结果是否足以阻止 MCP 回退。
 * <p>
 * 不能仅以“返回了 Chunk”作为命中，因为全局向量检索几乎总能返回若干结果。本判定同时检查
 * Rerank 分数与商品型号词，低质量/串型号结果按 MISS 处理。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeEvidenceEvaluator {

    private static final Pattern ASCII_TOKEN = Pattern.compile("[a-zA-Z]+|\\d+");

    private static final Set<String> IGNORED_ASCII_TOKENS = Set.of(
            "huawei",
            // 容量、单位与指标
            "gb", "tb", "mb", "mah", "w", "hz", "ghz", "mhz", "mm", "cm", "kg", "wh", "ppi", "mbps", "fps",
            // 功能与技术属性
            "wifi", "nfc", "cpu", "ram", "rom", "oled", "lcd", "ltpo", "pwm", "wlan", "bluetooth", "sim", "esim",
            "harmonyos", "android", "ios", "hdr", "gps", "hdmi", "ois"
    );

    private static final Set<String> PRODUCT_SCOPED_INTENTS = Set.of(
            "kb-buying-decision",
            "kb-product-comparison",
            "kb-product-parameters",
            "kb-harmony-compatibility",
            "kb-usage-guide",
            "mcp-vmall-search",
            "mcp-price-stock",
            "mcp-promotion",
            "mcp-installment-tradein"
    );

    private final McpFallbackProperties properties;

    public Assessment evaluate(String question, List<NodeScore> kbIntents, KbResult kbResult) {
        List<RetrievedChunk> chunks = kbResult == null ? List.of() : kbResult.chunks();
        if (CollUtil.isEmpty(chunks)) {
            return Assessment.miss("empty");
        }

        List<RetrievedChunk> relevant = eligibleChunks(chunks, properties.getMinRerankScore());
        float topScore = topScore(chunks);

        // 关闭回退能力时保持原行为：只要 KB 有结果就继续使用，不再应用新增的质量门槛。
        if (!properties.isEnabled()) {
            return Assessment.hit(chunks.size(), topScore);
        }

        if (relevant.size() < properties.getMinRelevantChunks()) {
            return Assessment.miss("low_score", relevant.size(), topScore);
        }

        if (properties.isRequireEntityMatch() && isProductScoped(kbIntents)) {
            Set<String> entityTokens = extractModelTokens(question);
            if (!entityTokens.isEmpty() && !containsEveryToken(relevant, entityTokens)) {
                return Assessment.miss("entity_mismatch", relevant.size(), topScore);
            }
        }
        return Assessment.hit(relevant.size(), topScore);
    }

    /**
     * MCP 全部失败后，对原始 KB 结果执行更严格限定的低分兜底判定。
     * 该判定不会绕过型号实体校验，也不会自行决定动态信息是否允许恢复。
     */
    public Assessment evaluateMcpFailureFallback(String question,
                                                 List<NodeScore> evidenceIntents,
                                                 KbResult kbResult) {
        McpFallbackProperties.McpFailureKbFallback fallback = properties.getMcpFailureKbFallback();
        if (!properties.isEnabled() || fallback == null || !fallback.isEnabled()) {
            return Assessment.miss("mcp_failure_kb_fallback_disabled");
        }

        List<RetrievedChunk> chunks = kbResult == null ? List.of() : kbResult.chunks();
        if (CollUtil.isEmpty(chunks)) {
            return Assessment.miss("empty");
        }

        List<RetrievedChunk> relevant = eligibleChunks(chunks, fallback.getMinRerankScore());
        float topScore = topScore(chunks);
        if (relevant.isEmpty()) {
            return Assessment.miss("fallback_low_score", 0, topScore);
        }

        if (properties.isRequireEntityMatch() && isProductScoped(evidenceIntents)) {
            Set<String> entityTokens = extractModelTokens(question);
            if (!entityTokens.isEmpty() && !containsEveryToken(relevant, entityTokens)) {
                return Assessment.miss("entity_mismatch", relevant.size(), topScore);
            }
        }
        return Assessment.hit("mcp_failure_kb_fallback", relevant.size(), topScore);
    }

    private List<RetrievedChunk> eligibleChunks(List<RetrievedChunk> chunks, double minScore) {
        return chunks.stream()
                .filter(chunk -> chunk != null && StrUtil.isNotBlank(chunk.getText()))
                .filter(chunk -> chunk.getScore() != null && chunk.getScore() >= minScore)
                .toList();
    }

    private float topScore(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .filter(java.util.Objects::nonNull)
                .map(RetrievedChunk::getScore)
                .filter(java.util.Objects::nonNull)
                .max(Float::compareTo)
                .orElse(Float.NaN);
    }

    private boolean isProductScoped(List<NodeScore> kbIntents) {
        return CollUtil.isNotEmpty(kbIntents) && kbIntents.stream()
                .map(NodeScore::getNode)
                .filter(java.util.Objects::nonNull)
                .map(node -> node.getId())
                .anyMatch(PRODUCT_SCOPED_INTENTS::contains);
    }

    /**
     * 只提取英文/数字型号词。纯中文品名不强行做规则切词，交给 Rerank 分数判断，避免把问法词误当商品名。
     */
    private Set<String> extractModelTokens(String question) {
        if (StrUtil.isBlank(question)) {
            return Set.of();
        }
        String normalizedQuestion = Normalizer.normalize(question, Normalizer.Form.NFKC);
        Matcher matcher = ASCII_TOKEN.matcher(normalizedQuestion);
        List<List<String>> tokenGroups = new ArrayList<>();
        List<String> currentGroup = new ArrayList<>();
        int previousEnd = -1;
        while (matcher.find()) {
            if (previousEnd >= 0 && containsWordCharacter(normalizedQuestion, previousEnd, matcher.start())) {
                tokenGroups.add(currentGroup);
                currentGroup = new ArrayList<>();
            }
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (!IGNORED_ASCII_TOKENS.contains(token)) {
                currentGroup.add(token);
            }
            previousEnd = matcher.end();
        }
        if (!currentGroup.isEmpty()) {
            tokenGroups.add(currentGroup);
        }

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (List<String> group : tokenGroups) {
            boolean hasModelWord = group.stream()
                    .anyMatch(token -> token.length() >= 2
                            && token.chars().anyMatch(Character::isLetter));
            if (hasModelWord) {
                tokens.addAll(group);
            }
        }
        return Set.copyOf(tokens);
    }

    private boolean containsWordCharacter(String value, int start, int end) {
        return value.substring(start, end).codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private boolean containsEveryToken(List<RetrievedChunk> chunks, Set<String> tokens) {
        String corpus = normalize(chunks.stream()
                .map(RetrievedChunk::getText)
                .filter(java.util.Objects::nonNull)
                .reduce("", (left, right) -> left + " " + right));
        return tokens.stream().allMatch(corpus::contains);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public enum Decision {
        HIT,
        MISS
    }

    public record Assessment(Decision decision, String reason, int relevantChunks, float topScore) {

        static Assessment hit(int relevantChunks, float topScore) {
            return hit("hit", relevantChunks, topScore);
        }

        static Assessment hit(String reason, int relevantChunks, float topScore) {
            return new Assessment(Decision.HIT, reason, relevantChunks, topScore);
        }

        static Assessment miss(String reason) {
            return new Assessment(Decision.MISS, reason, 0, Float.NaN);
        }

        static Assessment miss(String reason, int relevantChunks, float topScore) {
            return new Assessment(Decision.MISS, reason, relevantChunks, topScore);
        }

        public boolean isHit() {
            return decision == Decision.HIT;
        }
    }
}
