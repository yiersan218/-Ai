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

package com.nageoffer.ai.ragent.core.parser;

import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;

import java.util.Map;

/**
 * 文档解析器统一接口
 * <p>
 * 提供文档解析的通用能力，支持多种文档格式（PDF、Word、Excel、Markdown 等）
 * 可用于知识库导入、RAG 检索等场景。
 * <p>
 * <b>v1.1（多模态解析改造）</b>：核心接口为 {@link #parseStructured}，
 * 返回结构化 {@link ParsedDocument}（含 Block 列表）
 */
public interface DocumentParser {

    /**
     * 获取解析器类型标识
     *
     * @return 解析器类型（如 {@link ParserType#TIKA}、{@link ParserType#MARKDOWN}）
     */
    String getParserType();

    /**
     * 结构化解析：返回有序的 Block 列表（章节、段落、表格、图片等）
     * <p>
     * 所有 DocumentParser 必须实现本方法。Tika/Markdown 等老解析器可输出简化的
     * ParagraphBlock 列表作为过渡（M6 阶段升级为真正的 block 化解析）
     *
     * @param content  文档的二进制字节数组
     * @param mimeType 文档的 MIME 类型（可选）
     * @param options  解析选项（可选）
     * @return 结构化解析结果
     */
    ParsedDocument parseStructured(byte[] content, String mimeType, Map<String, Object> options);

    /**
     * 检查是否支持指定的 MIME 类型
     *
     * @param mimeType MIME 类型
     * @return 是否支持
     */
    default boolean supports(String mimeType) {
        return true;
    }
}
