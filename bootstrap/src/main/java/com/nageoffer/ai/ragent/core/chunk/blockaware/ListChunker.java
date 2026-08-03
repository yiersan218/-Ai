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

import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.core.parser.model.ListBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 列表 chunker：
 * <ul>
 *   <li>短列表（items.size() ≤ maxListItems）：atomic，整列表一个 chunk</li>
 *   <li>长列表：按 listItemsPerChunk 分组，每组一个 chunk</li>
 * </ul>
 * 渲染为标准 markdown 列表（{@code -} 或 {@code 1.}）
 */
@Component
public class ListChunker implements BlockChunker<ListBlock> {

    @Override
    public List<VectorChunk> chunk(ListBlock block, ChunkContext ctx) {
        if (block == null || block.items() == null || block.items().isEmpty()) {
            return List.of();
        }
        List<String> items = block.items();
        int max = ctx.config().maxListItems();

        if (items.size() <= max) {
            // 短列表 atomic
            return List.of(buildChunk(items, 1, block, ctx, ctx.startIndex()));
        }

        // 长列表按组切
        int per = ctx.config().listItemsPerChunk();
        List<VectorChunk> result = new ArrayList<>();
        int chunkIndex = ctx.startIndex();
        for (int i = 0; i < items.size(); i += per) {
            int end = Math.min(i + per, items.size());
            List<String> group = items.subList(i, end);
            result.add(buildChunk(group, i + 1, block, ctx, chunkIndex++));
        }
        return result;
    }

    /**
     * 构造列表 chunk。{@code startNumber} 仅对有序列表生效，作为本 chunk 起始编号。
     */
    private VectorChunk buildChunk(List<String> items, int startNumber, ListBlock block,
                                   ChunkContext ctx, int chunkIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (block.ordered()) {
                sb.append(startNumber + i).append(". ");
            } else {
                sb.append("- ");
            }
            sb.append(items.get(i)).append('\n');
        }
        // 去掉末尾的换行
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }

        return VectorChunk.builder()
                .chunkId(IdUtil.getSnowflakeNextIdStr())
                .index(chunkIndex)
                .content(sb.toString())
                .blockType("LIST")
                .outlinePath(new ArrayList<>(ctx.outlinePath()))
                .sourceBlockIds(List.of(block.id()))
                .build();
    }
}
