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

package com.nageoffer.ai.ragent.core.chunk;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nageoffer.ai.ragent.core.parser.model.AssetRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分块结果对象
 * 统一的分块输出格式，包含所有必要信息
 * <p>
 * <b>v1.1 新增字段</b>（多模态解析改造）：assets / blockType / outlinePath / sourceBlockIds / sectionContext
 * 所有新字段都有默认空值，老数据可空兼容（DB schema 无需改动，可序列化到 metadata 或独立列）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorChunk {

    /**
     * 块的唯一标识符
     */
    private String chunkId;

    /**
     * 块在文档中的序号索引，从0开始
     */
    private Integer index;

    /**
     * 块的原始文本内容
     * 用于展示与回填 LLM 上下文（表格场景为 markdown 表格）
     */
    private String content;

    /**
     * 嵌入专用文本，仅用于计算向量，不持久化、不展示
     * 为空时 {@link ChunkEmbeddingService} 回退到 {@link #content}
     * 表格 chunk 用 key-value 表示填充此字段（如 "姓名: 张三; 年龄: 25"），
     * 因 markdown 表格行的列名↔值靠位置对齐，embedding 模型读不懂位置，检索效果差
     */
    @JsonIgnore
    private String embeddingText;

    /**
     * 块的元数据信息
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 块的向量嵌入表示
     * 用于向量相似度检索的浮点数数组
     */
    @JsonIgnore
    private float[] embedding;

    /**
     * 资产引用列表（图片等）
     * 由 BlockAwareChunker 在切分阶段从 ImageBlock 等直接挂载，无需反向 grep markdown
     * 检索时用于将图片 URL 注入 LLM 上下文（本轮：前端 markdown 渲染展示;未来：多模态 LLM 输入）
     */
    @Builder.Default
    private List<AssetRef> assets = new ArrayList<>();

    /**
     * 块的来源类型，对应 {@link com.nageoffer.ai.ragent.core.parser.model.Block} 子类型
     * 取值如：HEADING / PARAGRAPH / TABLE / IMAGE / CODE / LIST / FORMULA
     * 检索时可按 blockType 分流重排序（表格走 BM25 + 向量，纯文本走纯向量等）
     */
    private String blockType;

    /**
     * 章节层级路径，如 ["第3章", "3.2 销售分析"]
     * 由 BlockAwareChunker 通过 HeadingHandler 累积注入
     * 检索时拼接进 user message，让 LLM 知道命中段落属于哪个章节
     */
    @Builder.Default
    private List<String> outlinePath = new ArrayList<>();

    /**
     * 来源 Block.id 列表，用于精确溯源
     * Table chunk 包含 1 个 TableBlock id；Paragraph chunk 可能包含多个 ParagraphBlock id（合并切分时）
     */
    @Builder.Default
    private List<String> sourceBlockIds = new ArrayList<>();

    /**
     * 节级上下文（如表头、sheet 名等），检索时可与 content 一起拼接给 LLM
     * 例如 TableChunker 把 sheetName + 表头摘要写入此字段，LLM 看到切碎的表格行也有上下文
     */
    private String sectionContext;
}
