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

package com.nageoffer.ai.ragent.core.parser.image;

import com.nageoffer.ai.ragent.core.parser.DocumentParser;
import com.nageoffer.ai.ragent.core.parser.ParserType;
import com.nageoffer.ai.ragent.core.parser.model.AssetRef;
import com.nageoffer.ai.ragent.core.parser.model.ImageBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.infra.vlm.VlmService;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 图片文档解析器（PNG / JPG / SVG）—— 写入侧「图生文」
 * <p>
 * 独立上传的图片本身没有可检索文本，直接 embedding {@code ![](url)} 等于噪声、永远召回不到。
 * 因此入库期用 VLM 把图片转成「中文描述 + 图中文字 OCR」作为可检索文本，同时把原图上传到
 * asset-bucket 供答复展示。产出单个带 {@code description} 的 {@link ImageBlock}：
 * <ul>
 *   <li>{@code description} 进 embedding，负责召回</li>
 *   <li>{@code asset.publicUrl} 由 {@link com.nageoffer.ai.ragent.core.chunk.blockaware.ImageChunker}
 *       渲染为 {@code ![caption](url)}，随答复返回、前端展示</li>
 * </ul>
 * <p>
 * SVG 是矢量 XML，VLM 视觉输入只认栅格格式，故先 {@link #rasterizeSvg} 渲染成 PNG 再并入 PNG 链路
 * <p>
 * 优先级高于 Tika（Tika 已对 image/* 返回 false），避免图片被当平文本兜底
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class ImageDocumentParser implements DocumentParser {

    public static final String OPT_SOURCE_FILE = "sourceFile";
    public static final String OPT_DOCUMENT_ID = "documentId";

    private final VlmService vlmService;
    private final FileStorageService fileStorageService;
    private final ImageParseProperties properties;

    public ImageDocumentParser(VlmService vlmService,
                               FileStorageService fileStorageService,
                               ImageParseProperties properties) {
        this.vlmService = vlmService;
        this.fileStorageService = fileStorageService;
        this.properties = properties;
    }

    @Override
    public String getParserType() {
        return ParserType.IMAGE.getType();
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String lower = mimeType.toLowerCase(Locale.ROOT);
        return lower.equals("image/png")
                || lower.equals("image/jpeg")
                || lower.equals("image/jpg")
                || lower.equals("image/svg+xml");
    }

    @Override
    public ParsedDocument parseStructured(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            throw new ServiceException("图片解析输入字节为空");
        }
        String sourceFile = extract(options, OPT_SOURCE_FILE, "");
        String documentId = extract(options, OPT_DOCUMENT_ID, UUID.randomUUID().toString());

        // 0. SVG 归一化：矢量 XML 栅格化成 PNG，此后字节与 mime 与 PNG 路径完全一致
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).equals("image/svg+xml")) {
            content = rasterizeSvg(content);
            mimeType = "image/png";
        }

        // 1. VLM 图生文（失败直接抛错，不兜底，避免产生"有图无描述"或"有描述无图"的残缺数据）
        // 直接取整段输出作描述，不解析任何分隔符 —— prompt 措辞可自由调整，解析器不耦合
        String description = vlmService.describeImage(
                content, mimeType, properties.getDescriptionPrompt(), properties.getMaxOutputTokens());
        description = description == null ? "" : description.strip();
        // 空描述等同失败：放过去只会产出「有图无描述」的纯链接 chunk，永远召回不到，故直接抛错暴露问题
        if (description.isBlank()) {
            throw new ServiceException("VLM 返回空描述，无法生成可检索文本：file=" + sourceFile);
        }

        // 2. 原图上传资产桶（public-read），拿匿名可达的公网 URL
        String ext = extFromMime(mimeType);
        String filename = "assets/" + documentId + "/" + UUID.randomUUID() + "." + ext;
        StoredFileDTO stored = fileStorageService.uploadAsset(content, filename, mimeType);
        String publicUrl = fileStorageService.getPublicUrl(stored.getUrl());

        // 3. 构造 ImageBlock：description 同时用于 content(展示/答题)与 embeddingText(向量，由 ImageChunker 去 URL)
        String blockId = UUID.randomUUID().toString();
        String caption = stripExt(sourceFile);
        AssetRef asset = new AssetRef(publicUrl, mimeType, blockId);
        ImageBlock block = new ImageBlock(blockId, Provenance.ofFile(sourceFile), List.of(),
                asset, caption, caption, description);

        log.info("图片图生文完成: file={}, descChars={}, url={}", sourceFile, description.length(), publicUrl);
        return ParsedDocument.of(List.of(block), Map.of(
                "parser", getParserType(),
                "mimeType", mimeType == null ? "" : mimeType,
                "descriptionChars", description.length()
        ));
    }

    private static String extract(Map<String, Object> options, String key, String defaultValue) {
        if (options == null) {
            return defaultValue;
        }
        Object v = options.get(key);
        return (v == null || v.toString().isBlank()) ? defaultValue : v.toString();
    }

    /**
     * SVG 栅格化成 PNG 字节
     * <p>
     * 必须铺白底：PNGTranscoder 默认透明背景，VLM 解码带 alpha 的 PNG 会把透明区合成为黑/空，
     * 导致模型「看不到内容」返回空描述。无内在尺寸的 SVG 设宽度上限避免超大画布；
     * 失败直接抛错，不产残缺数据（与「VLM 失败不兜底」一致）
     */
    private static byte[] rasterizeSvg(byte[] svg) {
        try {
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_MAX_WIDTH, 1600f);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transcoder.transcode(new TranscoderInput(new ByteArrayInputStream(svg)), new TranscoderOutput(out));
            return out.toByteArray();
        } catch (Exception e) {
            throw new ServiceException("SVG 栅格化失败：" + e.getMessage());
        }
    }

    private static String extFromMime(String mimeType) {
        if (mimeType == null) {
            return "png";
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "jpg";
            default -> "png";
        };
    }

    private static String stripExt(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
