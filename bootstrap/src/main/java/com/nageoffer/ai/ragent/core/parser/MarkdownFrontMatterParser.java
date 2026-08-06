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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提取 Markdown 文件开头的 YAML 风格 frontmatter。
 * <p>
 * 知识库 frontmatter 只使用标量和 JSON 风格数组，因此无需引入完整 YAML 运行时。
 * 无结束分隔符或无法识别的行会按普通 Markdown 正文处理，避免损坏用户文档。
 */
@Component
public class MarkdownFrontMatterParser {

    private static final Pattern FRONTMATTER = Pattern.compile(
            "\\A---\\R(?<header>.*?)\\R---(?:\\R|\\z)(?<body>.*)\\z",
            Pattern.DOTALL
    );

    private final ObjectMapper objectMapper;

    public MarkdownFrontMatterParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result parse(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return new Result("", Map.of());
        }

        String normalized = markdown.charAt(0) == '\uFEFF' ? markdown.substring(1) : markdown;

        Matcher matcher = FRONTMATTER.matcher(normalized);
        if (!matcher.matches()) {
            return new Result(normalized, Map.of());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        for (String line : matcher.group("header").split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                return new Result(normalized, Map.of());
            }
            String key = trimmed.substring(0, separator).trim();
            String rawValue = trimmed.substring(separator + 1).trim();
            if (!key.matches("[A-Za-z0-9_-]+")) {
                return new Result(normalized, Map.of());
            }
            metadata.put(key, parseValue(rawValue));
        }

        Object sourceUrls = metadata.get("source_urls");
        if (sourceUrls instanceof List<?> urls && !urls.isEmpty() && urls.get(0) != null) {
            metadata.putIfAbsent("canonical_url", urls.get(0).toString());
        }
        return new Result(matcher.group("body"), Map.copyOf(metadata));
    }

    private Object parseValue(String rawValue) {
        if (rawValue.isEmpty()) {
            return "";
        }
        if ((rawValue.startsWith("\"") && rawValue.endsWith("\""))
                || rawValue.startsWith("[") || rawValue.startsWith("{")) {
            try {
                return objectMapper.readValue(rawValue, Object.class);
            } catch (JsonProcessingException ignored) {
                // 保留原值，避免单个非标准字段阻断整篇文档导入。
            }
        }
        if (rawValue.length() >= 2 && rawValue.startsWith("'") && rawValue.endsWith("'")) {
            return rawValue.substring(1, rawValue.length() - 1).replace("''", "'");
        }
        if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
            return Boolean.parseBoolean(rawValue);
        }
        if (rawValue.matches("-?\\d+")) {
            try {
                return Long.parseLong(rawValue);
            } catch (NumberFormatException ignored) {
                return rawValue;
            }
        }
        if (rawValue.matches("-?\\d+\\.\\d+")) {
            return new BigDecimal(rawValue);
        }
        return rawValue;
    }

    public record Result(String body, Map<String, Object> metadata) {
    }
}
