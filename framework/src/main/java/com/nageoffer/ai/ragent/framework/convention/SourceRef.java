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

package com.nageoffer.ai.ragent.framework.convention;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回答来源引用（文档级）
 * <p>
 * 由检索片段按文档去重、赋号后得到，同时用于：SSE 下发、消息落库、前端来源面板与预览
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceRef {

    /**
     * 来源序号 从 1 开始 面板与将来行内角标共用同一编号
     */
    private Integer index;

    /**
     * 文档 ID 用于预览取原文
     */
    private String docId;

    /**
     * 文档名称 面板标题
     */
    private String docName;

    /**
     * 来源类型 file/url/feishu
     */
    private String sourceType;

    /**
     * 文件类型 md/xlsx/pdf/doc/图片等 前端据此为本地文件选类型图标 网页来源可为 null
     */
    private String fileType;

    /**
     * 外部原始链接 url/feishu 有 file 为 null（file 走 docId 预览提取正文）
     */
    private String url;

    /**
     * 摘录 取该文档最相关片段的截断文本
     */
    private String excerpt;
}
