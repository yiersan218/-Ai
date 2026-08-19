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

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.McpFallbackProperties;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.dto.KbResult;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeEvidenceEvaluatorTest {

    private final McpFallbackProperties properties = new McpFallbackProperties();
    private final KnowledgeEvidenceEvaluator evaluator = new KnowledgeEvidenceEvaluator(properties);

    @Test
    void acceptsHighScoreChunkContainingRequestedModel() {
        KnowledgeEvidenceEvaluator.Assessment assessment = evaluator.evaluate(
                "HUAWEI Mate 70 Pro 的处理器和屏幕参数",
                List.of(kbIntent("kb-product-parameters")),
                kbResult(chunk("HUAWEI Mate 70 Pro 配备高刷 OLED 屏幕", 0.91f))
        );

        assertTrue(assessment.isHit());
        assertEquals("hit", assessment.reason());
    }

    @Test
    void acceptsChunkAtNewNormalThreshold() {
        KnowledgeEvidenceEvaluator.Assessment assessment = evaluator.evaluate(
                "退货条件是什么",
                List.of(kbIntent("kb-return-refund")),
                kbResult(chunk("七天无理由退货规则", 0.50f))
        );

        assertTrue(assessment.isHit());
    }

    @Test
    void rejectsChunksBelowRerankThreshold() {
        KnowledgeEvidenceEvaluator.Assessment assessment = evaluator.evaluate(
                "退货条件是什么",
                List.of(kbIntent("kb-return-refund")),
                kbResult(chunk("七天无理由退货规则", 0.49f))
        );

        assertEquals(KnowledgeEvidenceEvaluator.Decision.MISS, assessment.decision());
        assertEquals("low_score", assessment.reason());
    }

    @Test
    void acceptsWeakChunkOnlyForMcpFailureFallback() {
        KbResult kbResult = kbResult(chunk("七天无理由退货规则", 0.40f));

        assertEquals(KnowledgeEvidenceEvaluator.Decision.MISS,
                evaluator.evaluate("退货条件是什么", List.of(kbIntent("kb-return-refund")), kbResult).decision());

        KnowledgeEvidenceEvaluator.Assessment fallbackAssessment = evaluator.evaluateMcpFailureFallback(
                "退货条件是什么", List.of(kbIntent("kb-return-refund")), kbResult);
        assertTrue(fallbackAssessment.isHit());
        assertEquals("mcp_failure_kb_fallback", fallbackAssessment.reason());
    }

    @Test
    void rejectsWeakFallbackBelowPointFour() {
        KnowledgeEvidenceEvaluator.Assessment assessment = evaluator.evaluateMcpFailureFallback(
                "退货条件是什么",
                List.of(kbIntent("kb-return-refund")),
                kbResult(chunk("七天无理由退货规则", 0.39f))
        );

        assertEquals(KnowledgeEvidenceEvaluator.Decision.MISS, assessment.decision());
        assertEquals("fallback_low_score", assessment.reason());
    }

    @Test
    void rejectsHighScoreChunkForAnotherProductModel() {
        KnowledgeEvidenceEvaluator.Assessment assessment = evaluator.evaluate(
                "HUAWEI Mate 70 Pro 的屏幕参数",
                List.of(kbIntent("kb-product-parameters")),
                kbResult(chunk("HUAWEI Pura 80 Pro 屏幕参数说明", 0.93f))
        );

        assertEquals(KnowledgeEvidenceEvaluator.Decision.MISS, assessment.decision());
        assertEquals("entity_mismatch", assessment.reason());
    }

    @Test
    void disablingFallbackPreservesExistingKnowledgeBehavior() {
        properties.setEnabled(false);

        KnowledgeEvidenceEvaluator.Assessment assessment = evaluator.evaluate(
                "HUAWEI Mate 70 Pro 的屏幕参数",
                List.of(kbIntent("kb-product-parameters")),
                kbResult(chunk("其他商品资料", 0.10f))
        );

        assertTrue(assessment.isHit());
    }

    @Test
    void doesNotTreatBudgetAsPartOfProductModel() {
        KnowledgeEvidenceEvaluator.Assessment assessment = evaluator.evaluate(
                "预算 5000 元推荐 MateBook",
                List.of(kbIntent("kb-buying-decision")),
                kbResult(chunk("MateBook 适合移动办公和学习场景", 0.88f))
        );

        assertTrue(assessment.isHit());
    }

    @Test
    void doesNotTreatMeasurementUnitsAsProductModel() {
        KnowledgeEvidenceEvaluator.Assessment assessment = evaluator.evaluate(
                "MateBook X Pro 的 GHz MHz mm cm kg Wh ppi Mbps fps 参数",
                List.of(kbIntent("kb-product-parameters")),
                kbResult(chunk("MateBook X Pro 参数说明", 0.88f))
        );

        assertTrue(assessment.isHit());
    }

    @Test
    void doesNotTreatGenericTechnicalAttributesAsProductModel() {
        KnowledgeEvidenceEvaluator.Assessment assessment = evaluator.evaluate(
                "MateBook X Pro CPU RAM ROM OLED LCD LTPO PWM WLAN Bluetooth SIM eSIM HarmonyOS Android iOS HDR GPS HDMI OIS",
                List.of(kbIntent("kb-product-parameters")),
                kbResult(chunk("MateBook X Pro 参数说明", 0.88f))
        );

        assertTrue(assessment.isHit());
    }

    @Test
    void dynamicMcpIntentAlsoRejectsAnotherProductModel() {
        KnowledgeEvidenceEvaluator.Assessment assessment = evaluator.evaluate(
                "HUAWEI Mate 70 Pro 价格是多少",
                List.of(mcpIntent("mcp-price-stock")),
                kbResult(chunk("HUAWEI Pura 80 Pro 知识库价格为 4599 元", 0.92f))
        );

        assertEquals(KnowledgeEvidenceEvaluator.Decision.MISS, assessment.decision());
        assertEquals("entity_mismatch", assessment.reason());
    }

    private NodeScore kbIntent(String id) {
        return new NodeScore(IntentNode.builder()
                .id(id)
                .name(id)
                .kind(IntentKind.KB)
                .build(), 0.9D);
    }

    private NodeScore mcpIntent(String id) {
        return new NodeScore(IntentNode.builder()
                .id(id)
                .name(id)
                .kind(IntentKind.MCP)
                .build(), 0.9D);
    }

    private RetrievedChunk chunk(String text, float score) {
        return RetrievedChunk.builder()
                .id("chunk-1")
                .text(text)
                .score(score)
                .build();
    }

    private KbResult kbResult(RetrievedChunk chunk) {
        return new KbResult("KB", Map.of("intent", List.of(chunk)));
    }
}
