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

/**
 * 资产引用：指向对象存储中已上传的二进制资源（图片等）
 * <p>
 * 由 MinerUResultUnpacker / Excel 等解析器在上传资产后构造，
 * 挂在 ImageBlock 上，并最终回填到 VectorChunk.assets 供检索使用
 *
 * @param publicUrl     浏览器可直连的公开预览 URL，如 "<a href="http://localhost:9000/ragent-assets/xxx.png">...</a>"（资产桶已开公共读）
 * @param mime          MIME 类型，如 "image/png"
 * @param sourceBlockId 关联的 Block.id()，用于溯源
 */
public record AssetRef(String publicUrl, String mime, String sourceBlockId) {
}
