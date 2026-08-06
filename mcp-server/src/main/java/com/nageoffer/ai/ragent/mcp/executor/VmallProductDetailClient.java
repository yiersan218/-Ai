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

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 读取华为商城商品详情页服务端渲染的 {@code __NEXT_DATA__}，提取公开的商品与 SKU 价格信息。
 * <p>
 * 这不是交易或库存接口。页面公开数据仍可能因地区、账号、活动和结算条件变化，调用方必须保留证据边界。
 */
final class VmallProductDetailClient {

    private static final int MAX_PAGE_CHARS = 5_000_000;

    private static final int MAX_SKU_COUNT = 20;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    /**
     * 可由离线测试替换为本地 stub。
     */
    String detailUrl = "https://www.vmall.com/product/comdetail/index.html";

    VmallProductDetailClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), new ObjectMapper());
    }

    VmallProductDetailClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    ProductDetail fetch(String prdId, String requestedSbomCode) throws Exception {
        requireNumericIdentifier("prdId", prdId);
        if (requestedSbomCode != null) {
            requireNumericIdentifier("sbomCode", requestedSbomCode);
        }

        String sourceUrl = buildDetailUrl(prdId, requestedSbomCode);
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("User-Agent", "Mozilla/5.0 (compatible; RagentMcp/1.0; +https://www.vmall.com/)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("华为商城商品页返回异常状态码: " + response.statusCode());
        }
        String html = response.body();
        if (html == null || html.isBlank()) {
            throw new IllegalStateException("华为商城商品页响应为空");
        }
        if (html.length() > MAX_PAGE_CHARS) {
            throw new IllegalStateException("华为商城商品页响应过大");
        }

        return parseProductDetail(extractNextData(html), prdId, requestedSbomCode, sourceUrl);
    }

    private ProductDetail parseProductDetail(String nextData,
                                             String requestedPrdId,
                                             String requestedSbomCode,
                                             String sourceUrl) throws Exception {
        JsonNode current = objectMapper.readTree(nextData)
                .path("props")
                .path("pageProps")
                .path("mainData")
                .path("current");
        if (current.isMissingNode() || current.isNull() || !current.isObject()) {
            throw new IllegalStateException("商品页缺少 mainData.current 结构化数据");
        }

        String pagePrdId = text(current, "disPrdId");
        if (!requestedPrdId.equals(pagePrdId)) {
            throw new IllegalStateException("商品页 prdId 与请求不一致");
        }

        String productName = firstNonBlank(text(current, "name"), text(current, "briefName"));
        if (productName == null) {
            throw new IllegalStateException("商品页缺少商品名称");
        }

        JsonNode base = current.path("base");
        if (!base.isObject() || base.size() == 0) {
            throw new IllegalStateException("商品页缺少 SKU 数据");
        }

        String defaultSbomCode = text(current, "currentSbomCode");
        List<SkuPrice> allSkus = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : base.properties()) {
            if (allSkus.size() >= MAX_SKU_COUNT) {
                break;
            }
            JsonNode sku = entry.getValue();
            String sbomCode = firstNonBlank(text(sku, "sbomCode"), entry.getKey());
            if (sbomCode == null) {
                continue;
            }
            allSkus.add(new SkuPrice(
                    sbomCode,
                    firstNonBlank(text(sku, "sbomAbbr"), text(sku, "sbomName"), productName),
                    decimal(sku, "price"),
                    decimal(sku, "originalPrice"),
                    attributes(sku.path("gbomAttrList")),
                    "1".equals(text(sku, "buttonMode")),
                    sku.path("defaultSbom").asInt(0) == 1 || sbomCode.equals(defaultSbomCode)
            ));
        }

        List<SkuPrice> selectedSkus = allSkus;
        if (requestedSbomCode != null) {
            selectedSkus = allSkus.stream()
                    .filter(sku -> requestedSbomCode.equals(sku.sbomCode()))
                    .toList();
            if (selectedSkus.isEmpty()) {
                throw new IllegalStateException("商品页未找到请求的 sbomCode");
            }
        }
        if (selectedSkus.isEmpty()) {
            throw new IllegalStateException("商品页没有可用 SKU 数据");
        }

        return new ProductDetail(
                productName,
                pagePrdId,
                defaultSbomCode,
                selectedSkus,
                benefits(base, defaultSbomCode),
                sourceUrl,
                Instant.now()
        );
    }

    private List<String> benefits(JsonNode base, String defaultSbomCode) {
        JsonNode defaultSku = defaultSbomCode == null ? null : base.path(defaultSbomCode);
        if (defaultSku == null || defaultSku.isMissingNode()) {
            defaultSku = base.elements().hasNext() ? base.elements().next() : null;
        }
        if (defaultSku == null) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        JsonNode benefitInfos = defaultSku.path("benefitInfos");
        if (benefitInfos.isArray()) {
            benefitInfos.forEach(item -> {
                String title = text(item, "title");
                String content = text(item, "content");
                String value = title == null ? content : content == null ? title : title + "：" + content;
                if (value != null && values.size() < 5) {
                    values.add(value);
                }
            });
        }
        return List.copyOf(values);
    }

    private List<String> attributes(JsonNode attrList) {
        if (!attrList.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        attrList.forEach(item -> {
            String name = text(item, "attrName");
            String value = text(item, "attrValue");
            if (name != null && value != null) {
                values.add(name + "=" + value);
            }
        });
        return List.copyOf(values);
    }

    private String buildDetailUrl(String prdId, String sbomCode) {
        StringBuilder url = new StringBuilder(detailUrl)
                .append(detailUrl.contains("?") ? '&' : '?')
                .append("prdId=")
                .append(URLEncoder.encode(prdId, StandardCharsets.UTF_8));
        if (sbomCode != null) {
            url.append("&sbomCode=")
                    .append(URLEncoder.encode(sbomCode, StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    private String extractNextData(String html) {
        int marker = html.indexOf("__NEXT_DATA__");
        if (marker < 0) {
            throw new IllegalStateException("商品页缺少 __NEXT_DATA__");
        }
        int contentStart = html.indexOf('>', marker);
        int contentEnd = contentStart < 0 ? -1 : html.indexOf("</script>", contentStart + 1);
        if (contentStart < 0 || contentEnd < 0 || contentEnd <= contentStart + 1) {
            throw new IllegalStateException("商品页 __NEXT_DATA__ 格式异常");
        }
        return html.substring(contentStart + 1, contentEnd).trim();
    }

    private static void requireNumericIdentifier(String name, String value) {
        if (value == null || !value.matches("\\d{4,32}")) {
            throw new IllegalArgumentException(name + " 参数格式不合法");
        }
    }

    private static BigDecimal decimal(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual() && !value.asText().isBlank()) {
            try {
                return new BigDecimal(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    record ProductDetail(String productName,
                         String prdId,
                         String defaultSbomCode,
                         List<SkuPrice> skus,
                         List<String> benefits,
                         String sourceUrl,
                         Instant fetchedAt) {
    }

    record SkuPrice(String sbomCode,
                    String skuName,
                    BigDecimal price,
                    BigDecimal originalPrice,
                    List<String> attributes,
                    boolean purchaseEntryVisible,
                    boolean defaultSku) {
    }
}
