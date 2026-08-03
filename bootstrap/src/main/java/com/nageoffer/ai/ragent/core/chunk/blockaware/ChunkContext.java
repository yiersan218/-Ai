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

package com.nageoffer.ai.ragent.core.chunk.blockaware;

import java.util.List;

/**
 * BlockChunker 调用上下文
 * <p>
 * 由 ChunkerNode 在遍历 Block 列表时构造并传入每个 chunker，承载：
 * <ul>
 *   <li>{@link #outlinePath}：当前 Block 所在的章节路径（由 HeadingHandler 累积）</li>
 *   <li>{@link #config}：切分参数（chunk 大小、表格 rowsPerChunk 等）</li>
 *   <li>{@link #startIndex}：当前 chunk 序号起点（用于 VectorChunk.index 单调递增）</li>
 * </ul>
 *
 * @param outlinePath 章节路径（不可变副本由调用方保证）
 * @param config      切分配置
 * @param startIndex  本次产出 VectorChunk 的起始 index
 */
public record ChunkContext(
        List<String> outlinePath,
        BlockChunkConfig config,
        int startIndex
) {

    public static ChunkContext of(List<String> outlinePath, BlockChunkConfig config) {
        return new ChunkContext(outlinePath, config, 0);
    }

    public static ChunkContext of(List<String> outlinePath, BlockChunkConfig config, int startIndex) {
        return new ChunkContext(outlinePath, config, startIndex);
    }
}
