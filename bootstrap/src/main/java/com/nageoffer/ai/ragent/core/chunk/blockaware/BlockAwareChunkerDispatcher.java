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

import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.CodeBlock;
import com.nageoffer.ai.ragent.core.parser.model.HeadingBlock;
import com.nageoffer.ai.ragent.core.parser.model.ImageBlock;
import com.nageoffer.ai.ragent.core.parser.model.ListBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParagraphBlock;
import com.nageoffer.ai.ragent.core.parser.model.TableBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockAwareChunker 调度器
 * <p>
 * 把 {@code List<Block>} 分发到各类型专属 chunker。HeadingHandler 不产 chunk，
 * 但累积 outlinePath 注入到后续 chunker 的 ChunkContext
 * <p>
 * 注：Java 17 中 sealed switch pattern 仍是 preview，用 instanceof pattern 链替代
 * Java 21 升级后可改回 switch 表达式
 */
@Component
@RequiredArgsConstructor
public class BlockAwareChunkerDispatcher {

    private final HeadingHandler headingHandler;
    private final ParagraphChunker paragraphChunker;
    private final TableChunker tableChunker;
    private final ImageChunker imageChunker;
    private final CodeChunker codeChunker;
    private final ListChunker listChunker;
    private final ChunkPacker chunkPacker;

    /**
     * 把 Block 列表分发到对应 chunker,返回有序 VectorChunk 列表
     *
     * @param blocks 解析器产出的 Block 列表
     * @param config 切分配置
     * @return VectorChunk 列表，index 单调递增
     */
    public List<VectorChunk> dispatch(List<Block> blocks, BlockChunkConfig config) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }

        List<String> outlinePath = List.of();
        List<VectorChunk> result = new ArrayList<>();
        int chunkIndex = 0;

        for (Block b : blocks) {
            if (b instanceof HeadingBlock h) {
                outlinePath = headingHandler.update(outlinePath, h);
                continue;
            }

            ChunkContext ctx = ChunkContext.of(outlinePath, config, chunkIndex);
            List<VectorChunk> chunks = chunkOne(b, ctx);
            result.addAll(chunks);
            chunkIndex += chunks.size();
        }
        // 后处理: 把相邻文本小块贪心打包到 maxChars, 断块处按 overlapChars 做块级重叠(单块 chunker 只拆不并)
        return chunkPacker.pack(result, config.maxChars(), config.overlapChars());
    }

    private List<VectorChunk> chunkOne(Block b, ChunkContext ctx) {
        if (b instanceof ParagraphBlock p) {
            return paragraphChunker.chunk(p, ctx);
        }
        if (b instanceof TableBlock t) {
            return tableChunker.chunk(t, ctx);
        }
        if (b instanceof ImageBlock i) {
            return imageChunker.chunk(i, ctx);
        }
        if (b instanceof CodeBlock c) {
            return codeChunker.chunk(c, ctx);
        }
        if (b instanceof ListBlock l) {
            return listChunker.chunk(l, ctx);
        }
        throw new IllegalStateException("Unsupported Block type: " + b.getClass().getName());
    }
}
