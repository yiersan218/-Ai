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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.ingestion.util.MimeTypeDetector;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownFrontMatterParserTest {

    private final MarkdownFrontMatterParser frontmatter = new MarkdownFrontMatterParser(new ObjectMapper());

    @Test
    void extractsTypedMetadataAndCanonicalUrl() {
        String markdown = """
                ---
                doc_id: "product-watch-5"
                scope: ["穿戴", "华为硬件"]
                dynamic_price_stock_excluded: true
                source_urls: ["https://www.vmall.com/watch-5", "https://consumer.huawei.com/watch-5"]
                ---
                # HUAWEI WATCH 5

                稳定卖点。
                """;

        MarkdownFrontMatterParser.Result result = frontmatter.parse(markdown);

        assertEquals("product-watch-5", result.metadata().get("doc_id"));
        assertEquals(List.of("穿戴", "华为硬件"), result.metadata().get("scope"));
        assertEquals(true, result.metadata().get("dynamic_price_stock_excluded"));
        assertEquals("https://www.vmall.com/watch-5", result.metadata().get("canonical_url"));
        assertTrue(result.body().startsWith("# HUAWEI WATCH 5"));
        assertFalse(result.body().contains("doc_id:"));
    }

    @Test
    void leavesOrdinaryOrUnclosedFrontmatterUntouched() {
        String ordinary = "# 标题\n\n正文";
        String unclosed = "---\ndoc_id: \"broken\"\n# 标题";

        assertEquals(ordinary, frontmatter.parse(ordinary).body());
        assertTrue(frontmatter.parse(ordinary).metadata().isEmpty());
        assertEquals(unclosed, frontmatter.parse(unclosed).body());
        assertTrue(frontmatter.parse(unclosed).metadata().isEmpty());
    }

    @Test
    void markdownParserExcludesHeaderFromBlocksAndKeepsMetadata() {
        MarkdownDocumentParser parser = new MarkdownDocumentParser(frontmatter);
        String markdown = """
                ---
                doc_id: "guide-reset"
                intent_node: "使用与选购指南 KB"
                ---
                # 恢复出厂设置

                请先备份数据。
                """;

        ParsedDocument parsed = parser.parseStructured(
                markdown.getBytes(StandardCharsets.UTF_8), "text/markdown", Map.of());

        assertEquals("guide-reset", parsed.metadata().get("doc_id"));
        assertEquals("Markdown", parsed.metadata().get("parser"));
        assertEquals(2, parsed.blocks().size());
    }

    @Test
    void routesWebMarkdownMimeToMarkdownParserInsteadOfTika() {
        MarkdownDocumentParser markdownParser = new MarkdownDocumentParser(frontmatter);
        TikaDocumentParser tikaParser = new TikaDocumentParser();
        DocumentParserSelector selector = new DocumentParserSelector(List.of(tikaParser, markdownParser));

        String detectedMime = MimeTypeDetector.detect(
                "---\ndoc_id: route-test\n---\n# 路由测试".getBytes(StandardCharsets.UTF_8),
                "route-test.md");

        assertEquals("text/x-web-markdown", detectedMime);
        assertTrue(markdownParser.supports("text/x-web-markdown"));
        assertTrue(markdownParser.supports("text/x-web-markdown; charset=UTF-8"));
        assertFalse(tikaParser.supports("text/x-web-markdown"));
        assertFalse(tikaParser.supports("text/x-web-markdown; charset=UTF-8"));
        assertSame(markdownParser, selector.selectByMimeType(detectedMime));
    }
}
