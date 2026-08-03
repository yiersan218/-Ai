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

import java.util.List;

/**
 * Block 类型专属的切分器
 * <p>
 * 每个 Block 子类型有独立的 chunker：
 * <ul>
 *   <li>HeadingHandler：累积 outlinePath，不产 chunk</li>
 *   <li>ParagraphChunker：按 token 切，不跨 heading</li>
 *   <li>TableChunker：按 rowsPerChunk + 表头重复</li>
 *   <li>ImageChunker：atomic，渲染 ![caption](http://...)</li>
 *   <li>CodeChunker：atomic（代码切碎危害大）</li>
 *   <li>ListChunker:短列表 atomic,长列表按项分组</li>
 * </ul>
 *
 * @param <B> 该 chunker 处理的 Block 子类型
 */
public interface BlockChunker<B extends Block> {

    /**
     * 把单个 Block 切分为若干 VectorChunk
     *
     * @param block 待切分的 Block
     * @param ctx   切分上下文（outlinePath + 配置 + 起始 index）
     * @return 切分结果（可能为空列表，如 HeadingHandler）
     */
    List<VectorChunk> chunk(B block, ChunkContext ctx);
}
