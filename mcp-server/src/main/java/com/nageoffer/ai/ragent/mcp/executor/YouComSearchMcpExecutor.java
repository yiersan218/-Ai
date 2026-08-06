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

package com.nageoffer.ai.ragent.mcp.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 华为商城导购场景的 You.com 联网搜索 MCP 工具
 * <p>
 * 基于 You.com Search API（GET <a href="https://ydc-index.io/v1/search">...</a>，X-API-Key 鉴权），
 * 按查询类型定向检索华为商城和华为消费者业务官网，返回带检索元数据、来源链接和摘录片段的官方网页结果；
 * API Key 从环境变量 YDC_API_KEY 读取。
 * <p>
 * 本工具是公开网页搜索，不是华为商城交易接口。价格、库存、优惠等动态信息只能作为搜索摘要证据返回，
 * 不保证与用户当前地区、账号或结算页实时状态一致。
 * <p>
 * 仅当环境变量 YDC_API_KEY 存在时才注册本工具（{@code @ConditionalOnProperty}）：工具清单是给 LLM
 * 消费的能力目录，登记一个缺 Key 不可用的工具只会诱导模型调用后失败、污染清单，故「工具存在 ⟺ 可用」，
 * 与 bootstrap 通道「无 Key 即不启用」对齐（Key 运行中失效的边界仍由 handleCall 内的校验兜底）
 * <p>
 * 说明：mcp-server 是零内部依赖、可独立部署的服务（不依赖 bootstrap / framework、与其不在同一 JVM，见各模块 pom），
 * 因此此处内置精简的 You.com HTTP 调用逻辑，与 bootstrap 的 {@code YouComWebSearchChannel} 属有意重复——
 * 抽公共模块会打破该隔离，故按「服务级重复」处理；修改 You.com 契约（端点 / 参数 / 响应结构）时两处需同步
 */
@Component
@ConditionalOnProperty(name = "YDC_API_KEY")
public class YouComSearchMcpExecutor {

    private static final Logger log = LoggerFactory.getLogger(YouComSearchMcpExecutor.class);

    private static final String TOOL_ID = "youcom_search";

    /**
     * API Key 环境变量名（团队约定，勿改）
     */
    private static final String ENV_API_KEY = "YDC_API_KEY";

    private static final String VMALL_DOMAIN = "vmall.com";

    private static final String HUAWEI_CONSUMER_DOMAIN = "consumer.huawei.com";

    /**
     * You.com 返回结果的严格白名单。注册域名同时覆盖其子域名。
     */
    private static final List<String> OFFICIAL_DOMAINS = List.of(VMALL_DOMAIN, HUAWEI_CONSUMER_DOMAIN);

    private static final List<String> VMALL_ONLY_DOMAINS = List.of(VMALL_DOMAIN);

    private static final int DEFAULT_COUNT = 5;

    private static final int MAX_COUNT = 20;

    private static final List<String> FRESHNESS_VALUES = List.of("day", "week", "month", "year");

    private static final String DEFAULT_SEARCH_TYPE = "general";

    private static final List<String> SEARCH_TYPE_VALUES = List.of(
            DEFAULT_SEARCH_TYPE,
            "product_search",
            "specifications",
            "price_stock",
            "promotion",
            "installment_tradein",
            "compatibility_support",
            "service_policy"
    );

    private static final int MAX_QUERY_LENGTH = 500;

    private static final int MAX_PRODUCT_NAME_LENGTH = 120;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * You.com Search API 地址（可测试性：单元测试可指向本地 stub 服务）
     */
    String apiUrl = "https://ydc-index.io/v1/search";

    @Bean
    public McpServerFeatures.SyncToolSpecification youComSearchToolSpecification() {
        return new McpServerFeatures.SyncToolSpecification(buildTool(),
                (exchange, request) -> handleCall(request));
    }

    private Tool buildTool() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("query", Map.of(
                "type", "string",
                "description", "用户要查询的完整商品问题；必须保留产品名、系列名、型号和用户给出的限制条件"
        ));

        properties.put("search_type", Map.of(
                "type", "string",
                "description", "查询主题：general(通用)、product_search(商品搜索)、specifications(参数规格)、price_stock(价格库存)、promotion(优惠活动)、installment_tradein(分期与以旧换新)、compatibility_support(兼容与使用支持)、service_policy(服务政策)",
                "enum", SEARCH_TYPE_VALUES,
                "default", DEFAULT_SEARCH_TYPE
        ));

        properties.put("product_name", Map.of(
                "type", "string",
                "description", "用户问题中明确出现的产品、系列或型号原文，例如 HUAWEI Sound X5；不得猜测或改写"
        ));

        properties.put("prd_id", Map.of(
                "type", "string",
                "description", "用户明确提供的华为商城 prdId；未提供时不要生成"
        ));

        properties.put("sbom_code", Map.of(
                "type", "string",
                "description", "用户明确提供的华为商城 sbomCode/SKU 标识；未提供时不要生成"
        ));

        properties.put("count", Map.of(
                "type", "integer",
                "description", "最多返回的结果条数（网页+新闻合计），默认 5，最大 20",
                "default", 5
        ));

        properties.put("freshness", Map.of(
                "type", "string",
                "description", "结果时效过滤：day(一天内)、week(一周内)、month(一月内)、year(一年内)，不传则不限",
                "enum", FRESHNESS_VALUES
        ));

        JsonSchema inputSchema = new JsonSchema(
                "object", properties, List.of("query"), null, null, null);

        return Tool.builder()
                .name(TOOL_ID)
                .description("通过 You.com Search API 面向华为商城智能导购检索 vmall.com 和华为官方支持页面：查询商品页、参数规格、价格库存搜索摘要、优惠活动、兼容支持和服务政策。动态信息不等同于交易系统实时结果。需要配置 YDC_API_KEY 环境变量")
                .inputSchema(inputSchema)
                .build();
    }

    CallToolResult handleCall(CallToolRequest request) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
            String query = stringArg(args, "query");
            String searchType = stringArg(args, "search_type");
            String productName = stringArg(args, "product_name");
            String prdId = stringArg(args, "prd_id");
            String sbomCode = stringArg(args, "sbom_code");
            Integer count = intArg(args, "count");
            String freshness = stringArg(args, "freshness");

            if (query == null || query.isBlank()) {
                return errorResult("请提供检索关键词 query");
            }
            query = query.trim();
            if (query.length() > MAX_QUERY_LENGTH) {
                return errorResult("query 过长，最多允许 " + MAX_QUERY_LENGTH + " 个字符");
            }
            if (searchType == null || searchType.isBlank()) searchType = DEFAULT_SEARCH_TYPE;
            if (!SEARCH_TYPE_VALUES.contains(searchType)) {
                return errorResult("search_type 参数不合法，可选值：" + String.join("、", SEARCH_TYPE_VALUES));
            }
            productName = trimToNull(productName);
            prdId = trimToNull(prdId);
            sbomCode = trimToNull(sbomCode);
            if (productName != null && productName.length() > MAX_PRODUCT_NAME_LENGTH) {
                return errorResult("product_name 过长，最多允许 " + MAX_PRODUCT_NAME_LENGTH + " 个字符");
            }
            if (count == null || count <= 0) count = DEFAULT_COUNT;
            if (count > MAX_COUNT) count = MAX_COUNT;
            if (freshness != null && !freshness.isBlank() && !FRESHNESS_VALUES.contains(freshness)) {
                return errorResult("freshness 参数不合法，可选值：" + String.join("、", FRESHNESS_VALUES));
            }

            String apiKey = readEnv(ENV_API_KEY);
            if (apiKey == null || apiKey.isBlank()) {
                return errorResult("You.com 联网搜索未配置：请先设置环境变量 YDC_API_KEY"
                        + "（可在 https://you.com/platform/api-keys 获取），配置后重启 MCP Server 即可使用");
            }

            List<String> domains = resolveSearchDomains(searchType);
            String effectiveQuery = buildEffectiveQuery(query, searchType, productName, prdId, sbomCode);
            String result = doSearch(query, effectiveQuery, searchType, count, freshness, domains, apiKey);

            log.info("MCP 工具调用完成, toolId={}, searchType={}, query={}, count={}, domains={}, elapsed={}ms",
                    TOOL_ID, searchType, query, count, domains, System.currentTimeMillis() - startMs);
            return successResult(result);
        } catch (Exception e) {
            log.error("MCP 工具调用失败, toolId={}, elapsed={}ms",
                    TOOL_ID, System.currentTimeMillis() - startMs, e);
            return errorResult("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 调用 You.com Search API 并格式化结果文本
     */
    private String doSearch(String originalQuery,
                            String effectiveQuery,
                            String searchType,
                            int count,
                            String freshness,
                            List<String> domains,
                            String apiKey) throws Exception {
        StringBuilder url = new StringBuilder(apiUrl)
                .append("?query=").append(URLEncoder.encode(effectiveQuery, StandardCharsets.UTF_8))
                .append("&count=").append(count)
                .append("&include_domains=")
                .append(URLEncoder.encode(String.join(",", domains), StandardCharsets.UTF_8));
        if (freshness != null && !freshness.isBlank()) {
            url.append("&freshness=").append(freshness);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(10))
                .header("X-API-Key", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            // 不回显响应体，避免泄露账号信息；401 鉴权失败 / 429 限流 / 5xx 服务端异常
            throw new IllegalStateException("You.com API 返回异常状态码: " + response.statusCode());
        }

        return formatResults(objectMapper.readTree(response.body()), count, originalQuery,
                effectiveQuery, searchType, domains, freshness);
    }

    /**
     * 把响应格式化为编号的 标题/链接/摘录 文本
     * <p>
     * 响应中 results.web 与 results.news 均可能缺失；
     * 每条结果中除 url/title/description/snippets 之外的字段均视为可选，防御式读取
     * <p>
     * You.com 的 count 是「每 section」语义（web、news 各最多 count 条），合并两段后统一截断到 count，
     * 使 count 对外表达「返回结果总条数上限」，与直觉一致，也避免多余结果占用 LLM token
     */
    private String formatResults(JsonNode root,
                                 int count,
                                 String originalQuery,
                                 String effectiveQuery,
                                 String searchType,
                                 List<String> domains,
                                 String freshness) {
        JsonNode results = root.path("results");
        List<JsonNode> items = new ArrayList<>();
        collectItems(items, results.path("web"), domains);
        collectItems(items, results.path("news"), domains);

        if (items.size() > count) {
            items = items.subList(0, count);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("工具: ").append(TOOL_ID).append('\n');
        sb.append("查询类型: ").append(searchType).append('\n');
        sb.append("用户查询: ").append(originalQuery).append('\n');
        sb.append("实际检索词: ").append(effectiveQuery).append('\n');
        sb.append("检索时间(UTC): ").append(Instant.now()).append('\n');
        sb.append("官方来源范围: ").append(String.join(", ", domains)).append('\n');
        sb.append("时效过滤: ").append(freshness == null || freshness.isBlank() ? "未限定" : freshness).append('\n');
        sb.append("证据级别: 公开网页搜索摘要（不是商品交易系统实时接口）\n");
        sb.append("动态信息说明: 价格、库存、优惠及活动状态可能受索引延迟、地区、账号和结算条件影响；仅能作为当前搜索摘要，最终以华为商城商品页或结算页展示为准。\n");

        if (items.isEmpty()) {
            sb.append("检索结果: 未检索到相关官方网页结果，请核对产品型号或更换关键词。");
            return sb.toString();
        }

        sb.append(String.format("检索结果数: %d\n\n", items.size()));
        int index = 1;
        for (JsonNode item : items) {
            String title = item.path("title").asText("(无标题)");
            String url = item.path("url").asText("");
            String excerpt = resolveExcerpt(item);
            String pageAge = item.path("page_age").asText("");

            sb.append(String.format("%d. %s\n", index++, title));
            sb.append("   来源类型: ").append(resolveSourceType(url)).append('\n');
            if (!url.isBlank()) {
                sb.append("   链接: ").append(url).append('\n');
            }
            appendProductIdentifiers(sb, url);
            if (!pageAge.isBlank()) {
                sb.append("   页面时间: ").append(pageAge).append('\n');
            }
            if (!excerpt.isBlank()) {
                sb.append("   摘录: ").append(excerpt).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 摘录优先取 description，缺失时回退第一条 snippet
     */
    private String resolveExcerpt(JsonNode item) {
        String description = item.path("description").asText("");
        if (!description.isBlank()) {
            return description;
        }
        JsonNode snippets = item.path("snippets");
        if (snippets.isArray() && !snippets.isEmpty()) {
            return snippets.get(0).asText("");
        }
        return "";
    }

    private void collectItems(List<JsonNode> items, JsonNode array, List<String> allowedDomains) {
        if (array != null && array.isArray()) {
            array.forEach(item -> {
                if (isAllowedResult(item, allowedDomains)) {
                    items.add(item);
                }
            });
        }
    }

    /**
     * 对 API 响应做第二层域名校验，避免异常或伪造的站外链接进入 RAG 上下文。
     */
    private boolean isAllowedResult(JsonNode item, List<String> allowedDomains) {
        String url = item.path("url").asText("");
        if (url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return allowedDomains.stream().anyMatch(domain ->
                    normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private List<String> resolveSearchDomains(String searchType) {
        return switch (searchType) {
            case "specifications", "compatibility_support", "service_policy" -> OFFICIAL_DOMAINS;
            default -> VMALL_ONLY_DOMAINS;
        };
    }

    private String buildEffectiveQuery(String query,
                                       String searchType,
                                       String productName,
                                       String prdId,
                                       String sbomCode) {
        StringBuilder effective = new StringBuilder();
        if (productName != null && !containsIgnoreCase(query, productName)) {
            effective.append(productName).append(' ');
        }
        effective.append(query.trim());
        appendIfMissing(effective, focusTerms(searchType));
        appendIfMissing(effective, prdId);
        appendIfMissing(effective, sbomCode);
        return effective.toString().replaceAll("\\s+", " ").trim();
    }

    private String focusTerms(String searchType) {
        return switch (searchType) {
            case "product_search" -> "商品 在售";
            case "specifications" -> "参数 规格 功能";
            case "price_stock" -> "价格 库存 在售";
            case "promotion" -> "优惠 活动";
            case "installment_tradein" -> "分期 以旧换新";
            case "compatibility_support" -> "连接 兼容 支持";
            case "service_policy" -> "配送 安装 退换货 保修 发票 售后";
            default -> null;
        };
    }

    private void appendIfMissing(StringBuilder target, String value) {
        if (value == null || value.isBlank() || containsIgnoreCase(target.toString(), value)) {
            return;
        }
        target.append(' ').append(value.trim());
    }

    private boolean containsIgnoreCase(String text, String value) {
        return text.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    private String resolveSourceType(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            if (host.equals(HUAWEI_CONSUMER_DOMAIN) || host.endsWith("." + HUAWEI_CONSUMER_DOMAIN)) {
                return path.contains("/support/") ? "华为官方支持页" : "华为消费者业务官网页面";
            }
            if (path.contains("comdetail") || path.contains("/product/") || path.contains("/item/")) {
                return "华为商城商品详情页";
            }
            if (path.contains("/search/")) {
                return "华为商城搜索页";
            }
            if (path.contains("/activity/")) {
                return "华为商城活动页";
            }
            return "华为商城公开页面";
        } catch (IllegalArgumentException ignored) {
            return "官方公开页面";
        }
    }

    private void appendProductIdentifiers(StringBuilder sb, String url) {
        String prdId = queryParameter(url, "prdId");
        String sbomCode = queryParameter(url, "sbomCode");
        if (prdId == null && sbomCode == null) {
            return;
        }
        sb.append("   商品标识:");
        if (prdId != null) sb.append(" prdId=").append(prdId);
        if (sbomCode != null) sb.append(" sbomCode=").append(sbomCode);
        sb.append('\n');
    }

    private String queryParameter(String url, String expectedName) {
        try {
            String rawQuery = URI.create(url).getRawQuery();
            if (rawQuery == null || rawQuery.isBlank()) return null;
            for (String pair : rawQuery.split("&")) {
                int separator = pair.indexOf('=');
                String rawName = separator >= 0 ? pair.substring(0, separator) : pair;
                if (!expectedName.equals(URLDecoder.decode(rawName, StandardCharsets.UTF_8))) continue;
                String rawValue = separator >= 0 ? pair.substring(separator + 1) : "";
                String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8).trim();
                return value.isEmpty() ? null : value;
            }
        } catch (IllegalArgumentException ignored) {
            // 非法 URL 已在白名单校验阶段过滤，这里只做防御式兜底。
        }
        return null;
    }

    /**
     * 读取环境变量（可测试性：单元测试可覆盖此方法屏蔽真实环境）
     */
    protected String readEnv(String name) {
        return System.getenv(name);
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Integer intArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        return null;
    }

    private static CallToolResult successResult(String text) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(text)))
                .isError(false)
                .build();
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
