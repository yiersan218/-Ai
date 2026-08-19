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
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.mcp.McpExtractionResult;
import com.nageoffer.ai.ragent.rag.core.mcp.McpParameterExtractor;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolExecutor;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolRegistry;
import com.nageoffer.ai.ragent.rag.core.prompt.ContextFormatter;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalEngineFallbackTest {

    private final ContextFormatter contextFormatter = mock(ContextFormatter.class);
    private final PromptTemplateLoader templateLoader = mock(PromptTemplateLoader.class);
    private final McpParameterExtractor parameterExtractor = mock(McpParameterExtractor.class);
    private final McpToolRegistry toolRegistry = mock(McpToolRegistry.class);
    private final MultiChannelRetrievalEngine knowledgeRetriever = mock(MultiChannelRetrievalEngine.class);
    private final McpToolExecutor toolExecutor = mock(McpToolExecutor.class);
    private final Tool tool = Tool.builder()
            .name("tencent_search")
            .description("test")
            .inputSchema(new JsonSchema("object", Map.of(), List.of(), null, null, null))
            .build();

    private McpFallbackProperties fallbackProperties;
    private RetrievalEngine retrievalEngine;

    @BeforeEach
    void setUp() {
        fallbackProperties = new McpFallbackProperties();
        fallbackProperties.setIntentMappings(Map.of(
                "kb-product-parameters", "specifications",
                "kb-return-refund", "service_policy"
        ));

        SearchChannelProperties searchProperties = new SearchChannelProperties();
        Executor directExecutor = Runnable::run;
        retrievalEngine = new RetrievalEngine(
                searchProperties,
                contextFormatter,
                templateLoader,
                parameterExtractor,
                toolRegistry,
                knowledgeRetriever,
                new KnowledgeEvidenceEvaluator(fallbackProperties),
                new McpFallbackPolicy(fallbackProperties),
                fallbackProperties,
                directExecutor,
                directExecutor
        );

        when(contextFormatter.formatKbContext(anyList(), anyMap(), anyInt())).thenReturn("KB");
        when(contextFormatter.formatMcpContext(anyMap(), anyList())).thenReturn("MCP");
        when(knowledgeRetriever.retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class)))
                .thenReturn(List.of());
        when(toolRegistry.getExecutor("tencent_search")).thenReturn(Optional.of(toolExecutor));
        when(toolExecutor.getToolDefinition()).thenReturn(tool);
        when(parameterExtractor.extractParameters(anyString(), any(Tool.class), anyString()))
                .thenReturn(McpExtractionResult.success(Map.of("query", "Mate 70 Pro")));
        when(toolExecutor.execute(anyMap())).thenReturn(CallToolResult.builder()
                .content(List.of(new TextContent("官方页面结果")))
                .isError(false)
                .build());
    }

    @Test
    void dynamicIntentUsesKnowledgeBaseWhenEvidenceHits() {
        when(knowledgeRetriever.retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class)))
                .thenReturn(List.of(chunk("HUAWEI Mate 70 Pro 知识库价格为 5699 元", 0.91f)));

        RetrievalContext context = retrievalEngine.retrieve(List.of(new SubQuestionIntent(
                "HUAWEI Mate 70 Pro 价格是多少",
                List.of(mcpIntent("mcp-price-stock"))
        )));

        assertTrue(context.hasKb());
        assertFalse(context.hasMcp());
        verify(knowledgeRetriever).retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class));
        verify(toolExecutor, never()).execute(anyMap());
    }

    @Test
    void dynamicIntentCallsMcpOnlyAfterKnowledgeMiss() {
        when(knowledgeRetriever.retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class)))
                .thenReturn(List.of(chunk("HUAWEI Pura 80 Pro 知识库价格为 4599 元", 0.92f)));

        RetrievalContext context = retrievalEngine.retrieve(List.of(new SubQuestionIntent(
                "HUAWEI Mate 70 Pro 价格是多少",
                List.of(mcpIntent("mcp-price-stock"))
        )));

        assertFalse(context.hasKb());
        assertTrue(context.hasMcp());
        verify(knowledgeRetriever).retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class));
        verify(toolExecutor).execute(anyMap());
    }

    @Test
    void knowledgeHitDoesNotCallFallbackMcp() {
        when(knowledgeRetriever.retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class)))
                .thenReturn(List.of(chunk("HUAWEI Mate 70 Pro 屏幕与处理器参数", 0.91f)));

        RetrievalContext context = retrievalEngine.retrieve(List.of(new SubQuestionIntent(
                "HUAWEI Mate 70 Pro 的屏幕参数",
                List.of(kbIntent("kb-product-parameters"))
        )));

        assertTrue(context.hasKb());
        assertFalse(context.hasMcp());
        verify(toolExecutor, never()).execute(anyMap());
    }

    @Test
    void knowledgeMissCallsFallbackMcpAndDropsWeakChunks() {
        when(knowledgeRetriever.retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class)))
                .thenReturn(List.of(chunk("HUAWEI Pura 80 Pro 屏幕参数", 0.92f)));

        RetrievalContext context = retrievalEngine.retrieve(List.of(new SubQuestionIntent(
                "HUAWEI Mate 70 Pro 的屏幕参数",
                List.of(kbIntent("kb-product-parameters"))
        )));

        assertFalse(context.hasKb());
        assertTrue(context.hasMcp());
        assertTrue(context.getIntentChunks().isEmpty());
        verify(toolExecutor).execute(anyMap());
    }

    @Test
    void mcpFailureRestoresTopThreeWeakStableKnowledgeChunks() {
        failMcp();
        when(knowledgeRetriever.retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class)))
                .thenReturn(List.of(
                        chunk("chunk-1", "退货规则 A", 0.40f),
                        chunk("chunk-2", "退货规则 B", 0.49f),
                        chunk("chunk-3", "退货规则 C", 0.45f),
                        chunk("chunk-4", "退货规则 D", 0.43f),
                        chunk("chunk-5", "无关片段", 0.39f)
                ));

        RetrievalContext context = retrievalEngine.retrieve(List.of(new SubQuestionIntent(
                "退货条件是什么",
                List.of(kbIntent("kb-return-refund"))
        )));

        assertTrue(context.hasKb());
        assertFalse(context.hasMcp());
        List<RetrievedChunk> selected = context.getIntentChunks().get("kb-return-refund");
        assertEquals(3, selected.size());
        assertEquals(List.of("chunk-2", "chunk-3", "chunk-4"),
                selected.stream().map(RetrievedChunk::getId).toList());
    }

    @Test
    void mcpFailureDoesNotRestoreKnowledgeBelowFallbackThreshold() {
        failMcp();
        when(knowledgeRetriever.retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class)))
                .thenReturn(List.of(chunk("退货规则", 0.39f)));

        RetrievalContext context = retrievalEngine.retrieve(List.of(new SubQuestionIntent(
                "退货条件是什么",
                List.of(kbIntent("kb-return-refund"))
        )));

        assertTrue(context.isEmpty());
    }

    @Test
    void mcpFailureDoesNotRestoreEntityMismatchKnowledge() {
        failMcp();
        when(knowledgeRetriever.retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class)))
                .thenReturn(List.of(chunk("HUAWEI Pura 80 Pro 屏幕参数", 0.45f)));

        RetrievalContext context = retrievalEngine.retrieve(List.of(new SubQuestionIntent(
                "HUAWEI Mate 70 Pro 的屏幕参数",
                List.of(kbIntent("kb-product-parameters"))
        )));

        assertTrue(context.isEmpty());
    }

    @Test
    void mcpFailureDoesNotRestoreWeakKnowledgeForDynamicIntent() {
        failMcp();
        when(knowledgeRetriever.retrieveKnowledgeChannels(anyList(), any(RetrievalBudget.class)))
                .thenReturn(List.of(chunk("HUAWEI Mate 70 Pro 知识库价格为 5699 元", 0.45f)));

        RetrievalContext context = retrievalEngine.retrieve(List.of(new SubQuestionIntent(
                "HUAWEI Mate 70 Pro 现在价格是多少",
                List.of(mcpIntent("mcp-price-stock"))
        )));

        assertTrue(context.isEmpty());
    }

    @Test
    void identicalMcpInvocationsAreExecutedOnlyOncePerTurn() {
        RetrievalContext context = retrievalEngine.retrieve(List.of(new SubQuestionIntent(
                "查询 Mate 70 Pro 的价格和库存",
                List.of(mcpIntent("mcp-price"), mcpIntent("mcp-stock"))
        )));

        assertTrue(context.hasMcp());
        verify(toolExecutor, times(1)).execute(anyMap());
    }

    @Test
    void uniqueMcpInvocationsRespectPerTurnCallLimit() {
        fallbackProperties.setMaxCallsPerTurn(1);
        when(parameterExtractor.extractParameters(anyString(), any(Tool.class), anyString()))
                .thenAnswer(invocation -> McpExtractionResult.success(Map.of("query", invocation.getArgument(0))));

        RetrievalContext context = retrievalEngine.retrieve(List.of(
                new SubQuestionIntent("查询 Mate 70 Pro 价格", List.of(mcpIntent("mcp-price"))),
                new SubQuestionIntent("查询 Pura 80 Pro 价格", List.of(mcpIntent("mcp-price")))
        ));

        assertTrue(context.hasMcp());
        verify(toolExecutor, times(1)).execute(anyMap());
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
                .mcpToolId("tencent_search")
                .paramPromptTemplate("输出 JSON")
                .build(), 0.9D);
    }

    private RetrievedChunk chunk(String text, float score) {
        return chunk("chunk-1", text, score);
    }

    private RetrievedChunk chunk(String id, String text, float score) {
        return RetrievedChunk.builder()
                .id(id)
                .text(text)
                .score(score)
                .build();
    }

    private void failMcp() {
        when(toolExecutor.execute(anyMap())).thenReturn(CallToolResult.builder()
                .content(List.of(new TextContent("腾讯云联网搜索超时")))
                .isError(true)
                .build());
    }
}
