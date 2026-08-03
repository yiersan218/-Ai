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

package com.nageoffer.ai.ragent.rag.core.vector.decorator;

import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.rag.core.graph.LightRagClient;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量写入的图谱同步装饰器
 * <p>
 * 与 {@link KeywordSyncingVectorStoreService} 同构：包裹真实的 {@link VectorStoreService}，
 * 在向量写入 / 删除成功后，同步维护 LightRAG 知识图谱，一处覆盖全部向量写调用点，无需改动任何调用方
 * <p>
 * 粒度：图谱抽取是文档级（跨 chunk 合并实体），故按文档同步，而非逐块
 * - indexDocumentChunks：以在手全量分块拼成全文写入（摄取 / 重建路径天然携带该文档全量分块）
 * - deleteDocumentVectors：按文档清除图谱；文档重建路径「先删后建」的既有调用顺序天然构成 upsert，无需额外去重
 * - updateChunk / deleteChunkById / deleteChunksByIds：子文档粒度，Phase1 不单独同步图谱
 *   （属稀有的人工分块编辑，整文重摄可刷新；避免为其引入 chunkId→docId 反查与文档级重建的额外机制）
 * <p>
 * 图谱写入为 best-effort：失败只记日志、不回滚向量、不中断主链路，因为图谱检索是增强而非必须能力
 */
@Slf4j
public class GraphSyncingVectorStoreService implements VectorStoreService {

    private final VectorStoreService delegate;
    private final LightRagClient lightRagClient;

    public GraphSyncingVectorStoreService(VectorStoreService delegate, LightRagClient lightRagClient) {
        this.delegate = delegate;
        this.lightRagClient = lightRagClient;
    }

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        delegate.indexDocumentChunks(collectionName, docId, chunks);
        syncGraph(docId, () -> lightRagClient.insertText(concatContent(chunks), fileSource(collectionName, docId)));
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        delegate.updateChunk(collectionName, docId, chunk);
        // 子文档粒度，Phase1 不单独同步图谱（见类注释）
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        delegate.deleteDocumentVectors(collectionName, docId);
        syncGraph(docId, () -> lightRagClient.deleteByDoc(docId));
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        delegate.deleteChunkById(collectionName, chunkId);
        // 子文档粒度，Phase1 不单独同步图谱（见类注释）
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        delegate.deleteChunksByIds(collectionName, chunkIds);
        // 子文档粒度，Phase1 不单独同步图谱（见类注释）
    }

    /**
     * 图谱 file_source 编码：{collectionName}_{docId}
     * 单文档删除按 docId token 命中、整库删除按 {collectionName}_ 前缀 token 命中，且不受服务端 file_path 归一化影响
     */
    private String fileSource(String collectionName, String docId) {
        return collectionName + "_" + docId;
    }

    /**
     * 拼接分块正文为文档全文，空白分块跳过
     */
    private String concatContent(List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        return chunks.stream()
                .map(VectorChunk::getContent)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * best-effort 执行图谱同步，失败仅告警，不影响向量主链路
     */
    private void syncGraph(String docId, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("图谱同步失败，已跳过 docId={}", docId, e);
        }
    }
}
