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

package com.nageoffer.ai.ragent.rag.dto;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * KB 检索结果
 *
 * @param groupedContext 分组后的上下文文本
 * @param intentChunks   意图 ID -> 分片列表
 */
public record KbResult(String groupedContext, Map<String, List<RetrievedChunk>> intentChunks) {

    /**
     * 返回去重后的原始命中片段，供知识证据质量判定使用。
     * 同一批 chunks 可能被分配给多个 KB 意图，不能按 map.values 直接计数。
     */
    public List<RetrievedChunk> chunks() {
        if (intentChunks == null || intentChunks.isEmpty()) {
            return List.of();
        }
        Map<String, RetrievedChunk> chunksById = new LinkedHashMap<>();
        Set<RetrievedChunk> anonymousChunks = Collections.newSetFromMap(new IdentityHashMap<>());
        List<RetrievedChunk> unique = new ArrayList<>();
        for (List<RetrievedChunk> chunks : intentChunks.values()) {
            if (chunks == null) {
                continue;
            }
            for (RetrievedChunk chunk : chunks) {
                if (chunk == null) {
                    continue;
                }
                if (chunk.getId() != null) {
                    if (chunksById.putIfAbsent(chunk.getId(), chunk) == null) {
                        unique.add(chunk);
                    }
                } else if (anonymousChunks.add(chunk)) {
                    unique.add(chunk);
                }
            }
        }
        return List.copyOf(unique);
    }

    /**
     * 空结果
     */
    public static KbResult empty() {
        return new KbResult("", Map.of());
    }
}
