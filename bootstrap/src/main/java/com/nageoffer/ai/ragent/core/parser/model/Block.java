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

package com.nageoffer.ai.ragent.core.parser.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * 结构化解析产物的统一基类（内存中间表示 IR）
 * <p>
 * Block 是解析器输出到 ChunkerNode 之间的中间表示，仅存活于解析阶段
 * 最终入向量库的 VectorChunk.content 仍是 markdown 字符串，markdown 在 ChunkerNode 阶段渲染
 * <p>
 * 关键设计：
 * <ul>
 *   <li>sealed interface 保证编译期穷举，新增 Block 类型时所有 switch 必须显式处理</li>
 *   <li>每个子类强类型字段，告别 Map&lt;String,Object&gt; 垃圾桶</li>
 *   <li>id() 提供唯一标识，供 AssetRef.sourceBlockId 与资产 key 规则引用</li>
 *   <li>markdown 不在 Block 上，chunker 渲染时按需生成</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HeadingBlock.class, name = "heading"),
        @JsonSubTypes.Type(value = ParagraphBlock.class, name = "paragraph"),
        @JsonSubTypes.Type(value = TableBlock.class, name = "table"),
        @JsonSubTypes.Type(value = ImageBlock.class, name = "image"),
        @JsonSubTypes.Type(value = CodeBlock.class, name = "code"),
        @JsonSubTypes.Type(value = ListBlock.class, name = "list")
})
public sealed interface Block permits HeadingBlock, ParagraphBlock, TableBlock, ImageBlock, CodeBlock, ListBlock {

    /**
     * 唯一标识，用于 AssetRef.sourceBlockId 与资产 key 规则
     */
    String id();

    /**
     * 来源信息：文件、页码 / sheet、bbox / 单元格范围
     */
    Provenance provenance();

    /**
     * 章节层级路径，如 ["第3章", "3.2 销售分析"]
     * 由 ChunkerNode 中的 HeadingHandler 累积注入 sectionContext
     */
    List<String> outlinePath();
}
