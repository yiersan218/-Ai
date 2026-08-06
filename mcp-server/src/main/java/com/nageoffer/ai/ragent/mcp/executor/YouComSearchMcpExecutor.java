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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 华为商城导购场景的 You.com 联网搜索 MCP 工具
 * <p>
 * 基于 You.com Search API（GET <a href="https://ydc-index.io/v1/search">...</a>，X-API-Key 鉴权），
 * 按查询类型定向检索华为商城和华为消费者业务官网，返回带检索元数据、来源链接和摘录片段的官方网页结果；
 * 对价格查询会继续读取严格匹配商品详情页公开的服务端渲染结构化数据，提取对应 SKU 的页面标价。
 * API Key 从环境变量 YDC_API_KEY 读取。
 * <p>
 * 本工具不是华为商城交易接口。商品详情页公开标价与搜索摘要都不保证与用户当前地区、账号或结算页实时状态一致。
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

    private static final Duration SEARCH_REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private static final int MAX_SEARCH_ATTEMPTS = 2;

    private static final int MAX_DETAIL_CANDIDATES = 3;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    VmallProductDetailClient productDetailClient = new VmallProductDetailClient();

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
                .description("通过 You.com Search API 面向华为商城智能导购检索 vmall.com 和华为官方支持页面；价格查询会对严格匹配的商城商品详情页补取公开结构化 SKU 标价。页面标价不等同于结算价，库存与个人权益不作保证。需要配置 YDC_API_KEY 环境变量")
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
            if (prdId != null && !prdId.matches("\\d{4,32}")) {
                return errorResult("prd_id 参数格式不合法");
            }
            if (sbomCode != null && !sbomCode.matches("\\d{4,32}")) {
                return errorResult("sbom_code 参数格式不合法");
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
            String result = doSearch(query, effectiveQuery, searchType, count, freshness, domains, apiKey,
                    productName, prdId, sbomCode);

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
                            String apiKey,
                            String productName,
                            String requestedPrdId,
                            String requestedSbomCode) throws Exception {
        StringBuilder url = new StringBuilder(apiUrl)
                .append("?query=").append(URLEncoder.encode(effectiveQuery, StandardCharsets.UTF_8))
                .append("&count=").append(count)
                .append("&include_domains=")
                .append(URLEncoder.encode(String.join(",", domains), StandardCharsets.UTF_8));
        if (freshness != null && !freshness.isBlank()) {
            url.append("&freshness=").append(freshness);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(SEARCH_REQUEST_TIMEOUT)
                .header("X-API-Key", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = sendSearchWithRetry(httpRequest);
        if (response.statusCode() != 200) {
            // 不回显响应体，避免泄露账号信息；401 鉴权失败 / 429 限流 / 5xx 服务端异常
            throw new IllegalStateException("You.com API 返回异常状态码: " + response.statusCode());
        }

        return formatResults(objectMapper.readTree(response.body()), count, originalQuery,
                effectiveQuery, searchType, domains, freshness, productName, requestedPrdId, requestedSbomCode);
    }

    private HttpResponse<String> sendSearchWithRetry(HttpRequest request) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_SEARCH_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (attempt < MAX_SEARCH_ATTEMPTS && isRetryableStatus(response.statusCode())) {
                    log.warn("You.com 搜索返回可重试状态, status={}, attempt={}/{}",
                            response.statusCode(), attempt, MAX_SEARCH_ATTEMPTS);
                    continue;
                }
                return response;
            } catch (HttpTimeoutException e) {
                lastException = e;
                if (attempt < MAX_SEARCH_ATTEMPTS) {
                    log.warn("You.com 搜索超时，立即重试, attempt={}/{}", attempt, MAX_SEARCH_ATTEMPTS);
                    continue;
                }
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_SEARCH_ATTEMPTS) {
                    log.warn("You.com 搜索网络异常，立即重试, attempt={}/{}, reason={}",
                            attempt, MAX_SEARCH_ATTEMPTS, e.getClass().getSimpleName());
                    continue;
                }
            }
        }
        throw lastException != null ? lastException : new IllegalStateException("You.com 搜索失败");
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
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
                                 String freshness,
                                 String productName,
                                 String requestedPrdId,
                                 String requestedSbomCode) {
        JsonNode results = root.path("results");
        List<JsonNode> items = new ArrayList<>();
        collectItems(items, results.path("web"), domains);
        collectItems(items, results.path("news"), domains);

        if (items.size() > count) {
            items = items.subList(0, count);
        }

        int originalItemCount = items.size();
        DetailLookup detailLookup = "price_stock".equals(searchType)
                ? lookupProductDetail(items, productName, requestedPrdId, requestedSbomCode)
                : DetailLookup.notRequested();
        if ("price_stock".equals(searchType) && (productName != null || requestedPrdId != null)) {
            items = verifiedProductItems(items, detailLookup.detail(), productName, requestedPrdId);
        }
        int ignoredItemCount = originalItemCount - items.size();

        StringBuilder sb = new StringBuilder();
        sb.append("工具: ").append(TOOL_ID).append('\n');
        sb.append("查询类型: ").append(searchType).append('\n');
        sb.append("用户查询: ").append(originalQuery).append('\n');
        sb.append("实际检索词: ").append(effectiveQuery).append('\n');
        sb.append("检索时间(UTC): ").append(Instant.now()).append('\n');
        sb.append("官方来源范围: ").append(String.join(", ", domains)).append('\n');
        sb.append("时效过滤: ").append(freshness == null || freshness.isBlank() ? "未限定" : freshness).append('\n');
        sb.append("搜索证据级别: 公开网页搜索摘要（不是商品交易系统实时接口）\n");
        sb.append("动态信息说明: 搜索摘要可能存在索引延迟；详情页公开标价也可能受地区、账号、活动和结算条件影响，最终以华为商城结算页展示为准。\n");

        appendProductDetail(sb, detailLookup);
        if (ignoredItemCount > 0) {
            sb.append("严格匹配过滤: 已忽略 ").append(ignoredItemCount)
                    .append(" 条与目标商品名称或 prdId 不一致的搜索结果。\n");
        }

        if (items.isEmpty()) {
            if ("price_stock".equals(searchType) && (productName != null || requestedPrdId != null)) {
                sb.append("检索结果: 未检索到与目标商品严格匹配的官方网页结果，请核对产品型号或更换关键词。");
            } else {
                sb.append("检索结果: 未检索到相关官方网页结果，请核对产品型号或更换关键词。");
            }
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

    private void appendProductDetail(StringBuilder sb, DetailLookup lookup) {
        if (!lookup.requested()) {
            return;
        }
        if (lookup.detail() == null) {
            sb.append("商品详情补取状态: 未获得可验证价格");
            if (lookup.message() != null) {
                sb.append("（").append(lookup.message()).append('）');
            }
            sb.append('\n');
            return;
        }

        VmallProductDetailClient.ProductDetail detail = lookup.detail();
        sb.append("商品详情补取状态: 成功\n");
        sb.append("详情页证据级别: 华为商城商品详情页公开的服务端渲染结构化数据（不是结算价或库存接口）\n");
        sb.append("页面商品: ").append(detail.productName()).append('\n');
        sb.append("商品标识: prdId=").append(detail.prdId()).append('\n');
        sb.append("详情读取时间(UTC): ").append(detail.fetchedAt()).append('\n');
        sb.append("详情链接: ").append(detail.sourceUrl()).append('\n');
        sb.append("页面公开 SKU 标价:\n");
        for (VmallProductDetailClient.SkuPrice sku : detail.skus()) {
            sb.append("- ").append(sku.skuName())
                    .append(" | sbomCode=").append(sku.sbomCode());
            if (!sku.attributes().isEmpty()) {
                sb.append(" | 配置=").append(String.join("，", sku.attributes()));
            }
            sb.append(" | 页面标价=").append(formatPrice(sku.price()));
            if (sku.price() != null && sku.originalPrice() != null
                    && sku.originalPrice().compareTo(sku.price()) > 0) {
                sb.append(" | 页面原价=").append(formatPrice(sku.originalPrice()));
            }
            sb.append(" | 标准购买入口=").append(sku.purchaseEntryVisible() ? "页面显示" : "页面未显示");
            if (sku.defaultSku()) {
                sb.append(" | 默认SKU");
            }
            sb.append('\n');
        }
        if (!detail.benefits().isEmpty()) {
            sb.append("页面权益提示: ").append(String.join("；", detail.benefits())).append('\n');
        }
        sb.append("状态边界: “标准购买入口=页面显示”不等同于已确认实时库存；标价和权益以用户实际结算页为准。\n");
    }

    private DetailLookup lookupProductDetail(List<JsonNode> items,
                                              String productName,
                                              String requestedPrdId,
                                              String requestedSbomCode) {
        if (productName == null && requestedPrdId == null) {
            return DetailLookup.failed("缺少可校验的商品名称或 prdId");
        }

        Set<String> candidatePrdIds = new LinkedHashSet<>();
        if (requestedPrdId != null) {
            candidatePrdIds.add(requestedPrdId);
        } else {
            for (JsonNode item : items) {
                String title = item.path("title").asText("");
                String candidatePrdId = queryParameter(item.path("url").asText(""), "prdId");
                if (candidatePrdId != null && isStrictProductTitleMatch(title, productName)) {
                    candidatePrdIds.add(candidatePrdId);
                    if (candidatePrdIds.size() >= MAX_DETAIL_CANDIDATES) {
                        break;
                    }
                }
            }
        }

        if (candidatePrdIds.isEmpty()) {
            return DetailLookup.failed("搜索结果中没有与目标商品严格匹配且包含 prdId 的详情页");
        }

        String lastFailure = null;
        for (String candidatePrdId : candidatePrdIds) {
            try {
                VmallProductDetailClient.ProductDetail detail = productDetailClient.fetch(
                        candidatePrdId, requestedSbomCode);
                if (productName != null && !isSameProduct(detail.productName(), productName)) {
                    lastFailure = "详情页商品名称与查询商品不一致";
                    continue;
                }
                return DetailLookup.success(detail);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return DetailLookup.failed("商品详情页请求已取消");
                }
                lastFailure = safeDetailFailure(e);
                log.warn("华为商城商品详情补取失败, prdId={}, reason={}",
                        candidatePrdId, e.getClass().getSimpleName());
            }
        }
        return DetailLookup.failed(lastFailure != null ? lastFailure : "没有可验证的商品详情数据");
    }

    private List<JsonNode> verifiedProductItems(List<JsonNode> items,
                                                VmallProductDetailClient.ProductDetail detail,
                                                String productName,
                                                String requestedPrdId) {
        String verifiedPrdId = detail != null ? detail.prdId() : requestedPrdId;
        return items.stream()
                .filter(item -> {
                    String url = item.path("url").asText("");
                    if (verifiedPrdId != null) {
                        return verifiedPrdId.equals(queryParameter(url, "prdId"));
                    }
                    return productName != null
                            && isStrictProductTitleMatch(item.path("title").asText(""), productName);
                })
                .toList();
    }

    private boolean isStrictProductTitleMatch(String title, String productName) {
        if (title == null || title.isBlank() || productName == null || productName.isBlank()) {
            return false;
        }
        String normalizedTitle = normalizeProductName(title);
        String normalizedProduct = normalizeProductName(productName);
        int matchIndex = normalizedTitle.indexOf(normalizedProduct);
        if (normalizedProduct.length() < 3 || matchIndex < 0) {
            return false;
        }
        String remainder = normalizedTitle.substring(matchIndex + normalizedProduct.length());
        return !startsWithVariantQualifier(remainder);
    }

    private boolean isSameProduct(String pageProductName, String requestedProductName) {
        String page = normalizeProductName(pageProductName);
        String requested = normalizeProductName(requestedProductName);
        if (page.equals(requested)) {
            return true;
        }
        if (requested.startsWith(page)) {
            return !startsWithVariantQualifier(requested.substring(page.length()));
        }
        if (page.startsWith(requested)) {
            return !startsWithVariantQualifier(page.substring(requested.length()));
        }
        return false;
    }

    private String normalizeProductName(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[（(][^）)]*[）)]", " ")
                .toLowerCase(Locale.ROOT)
                .replace("huawei", "")
                .replace("华为", "");
        return normalized.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private boolean startsWithVariantQualifier(String value) {
        return value.startsWith("pro")
                || value.startsWith("max")
                || value.startsWith("plus")
                || value.startsWith("ultra")
                || value.startsWith("air")
                || value.startsWith("se");
    }

    private String safeDetailFailure(Exception exception) {
        if (exception instanceof HttpTimeoutException) {
            return "商品详情页请求超时";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "商品详情页读取失败";
        }
        return message.length() <= 120 ? message : message.substring(0, 120);
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "未提供";
        }
        return price.stripTrailingZeros().toPlainString() + " 元";
    }

    /**
     * 合并 description 与最多两条不重复的 snippet，避免搜索 API 把价格等关键信息仅放在 snippets 时被丢弃。
     */
    private String resolveExcerpt(JsonNode item) {
        LinkedHashSet<String> excerpts = new LinkedHashSet<>();
        String description = item.path("description").asText("");
        if (!description.isBlank()) {
            excerpts.add(compactExcerpt(description));
        }
        JsonNode snippets = item.path("snippets");
        if (snippets.isArray()) {
            snippets.forEach(snippet -> {
                if (excerpts.size() < 3 && snippet.isTextual() && !snippet.asText().isBlank()) {
                    excerpts.add(compactExcerpt(snippet.asText()));
                }
            });
        }
        return String.join("；补充片段：", excerpts);
    }

    private String compactExcerpt(String value) {
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 800 ? compact : compact.substring(0, 800) + "…";
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

    private record DetailLookup(boolean requested,
                                VmallProductDetailClient.ProductDetail detail,
                                String message) {

        private static DetailLookup notRequested() {
            return new DetailLookup(false, null, null);
        }

        private static DetailLookup success(VmallProductDetailClient.ProductDetail detail) {
            return new DetailLookup(true, detail, null);
        }

        private static DetailLookup failed(String message) {
            return new DetailLookup(true, null, message);
        }
    }
}
