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
import com.nageoffer.ai.ragent.core.parser.model.AssetRef;
import com.nageoffer.ai.ragent.core.parser.model.ImageBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片 chunker：每个 ImageBlock 产生一个 atomic VectorChunk
 * <p>
 * 渲染为 markdown 图片链接 {@code ![caption](http://...)}，保证整段不会被切碎
 * 同时把 ImageBlock 的 AssetRef 挂载到 VectorChunk.assets，检索时可用
 */
@Component
public class ImageChunker implements BlockChunker<ImageBlock> {

    @Override
    public List<VectorChunk> chunk(ImageBlock block, ChunkContext ctx) {
        if (block == null || block.asset() == null) {
            return List.of();
        }
        AssetRef asset = block.asset();

        String visible = pickCaption(block);
        String markdown = "![" + visible + "](" + asset.publicUrl() + ")";

        // content(展示+答题):自包含描述在前 + 图片 markdown 在后;无描述(如 MinerU 抽图)回落为纯链接
        String description = block.description();
        boolean hasDescription = description != null && !description.isBlank();
        String content = hasDescription
                ? description.strip() + "\n\n" + markdown
                : markdown;

        // embeddingText(只做向量):用描述原文,去掉 ![](url) 那行 URL 噪声;
        // 无描述则置 null,由 ChunkEmbeddingService 回退 content(MinerU 老行为不变)
        String embeddingText = hasDescription ? description.strip() : null;

        VectorChunk chunk = VectorChunk.builder()
                .chunkId(IdUtil.getSnowflakeNextIdStr())
                .index(ctx.startIndex())
                .content(content)
                .embeddingText(embeddingText)
                .blockType("IMAGE")
                .outlinePath(new ArrayList<>(ctx.outlinePath()))
                .sourceBlockIds(List.of(block.id()))
                .assets(List.of(asset))
                .sectionContext(buildSectionContext(block))
                .build();

        return List.of(chunk);
    }

    private String pickCaption(ImageBlock block) {
        if (block.caption() != null && !block.caption().isEmpty()) {
            return block.caption();
        }
        if (block.altText() != null && !block.altText().isEmpty()) {
            return block.altText();
        }
        return "";
    }

    private String buildSectionContext(ImageBlock block) {
        if (block.provenance() == null || block.provenance().sheetName() == null) {
            return null;
        }
        return "sheet=" + block.provenance().sheetName();
    }
}
