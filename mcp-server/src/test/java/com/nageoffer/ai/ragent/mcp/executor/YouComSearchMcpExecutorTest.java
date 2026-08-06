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

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * youcom_search MCP 工具单元测试（离线，使用 JDK 内置 HttpServer 作为 stub，不依赖真实 Key）
 */
@DisplayName("youcom_search MCP 工具")
class YouComSearchMcpExecutorTest {

    private static final String SAMPLE_BODY = """
            {
              "results": {
                "web": [
                  {
                    "url": "https://www.vmall.com/product/comdetail/index.html?prdId=1001&sbomCode=2002",
                    "title": "网页结果A",
                    "description": "描述A",
                    "snippets": ["片段A1"]
                  },
                  {
                    "url": "https://vmall.com.evil.example/external",
                    "title": "站外结果",
                    "description": "该结果必须被过滤"
                  },
                  {
                    "url": "https://m.vmall.com/product/b",
                    "title": "网页结果B",
                    "snippets": ["片段B1"]
                  }
                ],
                "news": [
                  {"url": "https://www.vmall.com/news", "title": "新闻结果", "description": "新闻描述"}
                ]
              }
            }
            """;

    private static final String PRODUCT_DETAIL_BODY = """
            <!doctype html><html><body>
            <script id="__NEXT_DATA__" type="application/json">
            {
              "props": {
                "pageProps": {
                  "mainData": {
                    "current": {
                      "name": "华为畅享 90",
                      "briefName": "华为畅享 90",
                      "disPrdId": 10086174757473,
                      "currentSbomCode": "2601010613210",
                      "base": {
                        "2601010613207": {
                          "sbomCode": "2601010613207",
                          "sbomAbbr": "华为畅享 90 128GB 星空黑",
                          "price": 1299,
                          "buttonMode": "1",
                          "defaultSbom": 0,
                          "gbomAttrList": [
                            {"attrName": "颜色", "attrValue": "星空黑"},
                            {"attrName": "版本", "attrValue": "128GB"}
                          ]
                        },
                        "2601010613210": {
                          "sbomCode": "2601010613210",
                          "sbomAbbr": "华为畅享 90 256GB 星空黑",
                          "price": 1599,
                          "originalPrice": 1699,
                          "buttonMode": "1",
                          "defaultSbom": 1,
                          "benefitInfos": [
                            {"title": "分期", "content": "至高可享 6 期 0 分期利息"}
                          ],
                          "gbomAttrList": [
                            {"attrName": "颜色", "attrValue": "星空黑"},
                            {"attrName": "版本", "attrValue": "256GB"}
                          ]
                        }
                      }
                    }
                  }
                }
              }
            }
            </script></body></html>
            """;

    private HttpServer server;
    private String stubBaseUrl;
    private String stubUrl;

    private final AtomicReference<String> lastApiKey = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> lastQueryParams = new AtomicReference<>();
    private final AtomicInteger searchRequestCount = new AtomicInteger();
    private final AtomicInteger detailRequestCount = new AtomicInteger();

    private volatile int responseCode = 200;
    private volatile String responseBody = SAMPLE_BODY;
    private volatile int searchFailuresBeforeSuccess;
    private volatile int detailResponseCode = 200;
    private volatile String detailResponseBody = PRODUCT_DETAIL_BODY;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/search", exchange -> {
            int requestNumber = searchRequestCount.incrementAndGet();
            lastApiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            lastQueryParams.set(parseQuery(exchange.getRequestURI().getRawQuery()));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            int actualResponseCode = requestNumber <= searchFailuresBeforeSuccess ? 503 : responseCode;
            exchange.sendResponseHeaders(actualResponseCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/product/comdetail/index.html", exchange -> {
            detailRequestCount.incrementAndGet();
            byte[] bytes = detailResponseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(detailResponseCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        stubBaseUrl = "http://localhost:" + server.getAddress().getPort();
        stubUrl = stubBaseUrl + "/v1/search";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("工具 Schema：名称、必填参数与可选参数定义正确")
    void toolSchema() {
        Tool tool = new YouComSearchMcpExecutor().youComSearchToolSpecification().tool();

        assertEquals("youcom_search", tool.name());
        assertTrue(tool.description().contains("You.com"));
        assertTrue(tool.description().contains("vmall.com"));
        assertTrue(tool.description().contains("YDC_API_KEY"));
        assertEquals(java.util.List.of("query"), tool.inputSchema().required());
        assertTrue(tool.inputSchema().properties().containsKey("query"));
        assertTrue(tool.inputSchema().properties().containsKey("search_type"));
        assertTrue(tool.inputSchema().properties().containsKey("product_name"));
        assertTrue(tool.inputSchema().properties().containsKey("prd_id"));
        assertTrue(tool.inputSchema().properties().containsKey("sbom_code"));
        assertTrue(tool.inputSchema().properties().containsKey("count"));
        assertTrue(tool.inputSchema().properties().containsKey("freshness"));
    }

    @Test
    @DisplayName("缺少 query 参数返回 errorResult")
    void missingQueryReturnsError() {
        CallToolResult result = executor("any-key").handleCall(request(Map.of()));

        assertTrue(result.isError());
        assertTrue(text(result).contains("query"));
    }

    @Test
    @DisplayName("缺少 YDC_API_KEY 返回带配置指引的 errorResult")
    void missingKeyReturnsFriendlyError() {
        CallToolResult result = executor(null).handleCall(request(Map.of("query", "test")));

        assertTrue(result.isError());
        assertTrue(text(result).contains("YDC_API_KEY"));
        assertTrue(text(result).contains("you.com/platform/api-keys"));
    }

    @Test
    @DisplayName("freshness 参数不合法返回 errorResult")
    void invalidFreshnessReturnsError() {
        CallToolResult result = executor("k").handleCall(
                request(Map.of("query", "test", "freshness", "hour")));

        assertTrue(result.isError());
        assertTrue(text(result).contains("freshness"));
    }

    @Test
    @DisplayName("search_type 参数不合法返回 errorResult")
    void invalidSearchTypeReturnsError() {
        CallToolResult result = executor("k").handleCall(
                request(Map.of("query", "test", "search_type", "realtime_order")));

        assertTrue(result.isError());
        assertTrue(text(result).contains("search_type"));
    }

    @Test
    @DisplayName("成功检索：请求携带 X-API-Key，结果格式化为编号的标题/链接/摘录")
    void successMapping() {
        CallToolResult result = executor("test-key").handleCall(
                request(Map.of("query", "什么是 RAG", "count", 3, "freshness", "week")));

        assertFalse(result.isError());
        assertEquals("test-key", lastApiKey.get());
        assertEquals("什么是 RAG", lastQueryParams.get().get("query"));
        assertEquals("3", lastQueryParams.get().get("count"));
        assertEquals("vmall.com", lastQueryParams.get().get("include_domains"));
        assertEquals("week", lastQueryParams.get().get("freshness"));

        String text = text(result);
        assertTrue(text.contains("检索结果数: 3"));
        assertTrue(text.contains("证据级别: 公开网页搜索摘要"));
        assertTrue(text.contains("1. 网页结果A"));
        assertTrue(text.contains("链接: https://www.vmall.com/product/comdetail/index.html?prdId=1001&sbomCode=2002"));
        assertTrue(text.contains("商品标识: prdId=1001 sbomCode=2002"));
        assertTrue(text.contains("摘录: 描述A"));
        // description 缺失时回退第一条 snippet
        assertTrue(text.contains("摘录: 片段B1"));
        // news 结果也在列表内
        assertTrue(text.contains("3. 新闻结果"));
        // 即使 API 异常返回站外链接，响应层也必须再次过滤
        assertFalse(text.contains("evil.example"));
        assertFalse(text.contains("站外结果"));
    }

    @Test
    @DisplayName("count 参数：超过上限截断为 20，非法值回退默认 5")
    void countDefaultAndCap() {
        executor("k").handleCall(request(Map.of("query", "t", "count", 50)));
        assertEquals("20", lastQueryParams.get().get("count"));

        executor("k").handleCall(request(Map.of("query", "t", "count", -1)));
        assertEquals("5", lastQueryParams.get().get("count"));
    }

    @Test
    @DisplayName("价格库存查询保留型号并补充查询主题和商城标识")
    void priceStockBuildsCommerceQuery() {
        CallToolResult result = executor("k").handleCall(request(Map.of(
                "query", "Sound X5 多少钱",
                "search_type", "price_stock",
                "product_name", "HUAWEI Sound X5",
                "prd_id", "10086679107439",
                "sbom_code", "3102060025003"
        )));

        assertFalse(result.isError());
        assertEquals("HUAWEI Sound X5 Sound X5 多少钱 价格 库存 在售 10086679107439 3102060025003",
                lastQueryParams.get().get("query"));
        assertEquals("vmall.com", lastQueryParams.get().get("include_domains"));
        assertTrue(text(result).contains("查询类型: price_stock"));
        assertTrue(text(result).contains("动态信息说明:"));
    }

    @Test
    @DisplayName("价格查询严格匹配商品并补取详情页 SKU 标价")
    void priceStockEnrichesVerifiedProductDetail() {
        responseBody = """
                {"results":{"web":[
                  {
                    "url":"https://item.vmall.com/product/comdetail/index.html?prdId=10086454848882",
                    "title":"HUAWEI Pura 90 Pro Max",
                    "description":"错误候选",
                    "snippets":["¥6499"]
                  },
                  {
                    "url":"https://www.vmall.com/product/comdetail/index.html?prdId=10086174757473&sbomCode=2601010613210",
                    "title":"华为畅享 90 256GB 星空黑",
                    "description":"官方商品页"
                  }
                ]}}
                """;

        CallToolResult result = executor("k").handleCall(request(Map.of(
                "query", "华为畅享 90的价格",
                "search_type", "price_stock",
                "product_name", "华为畅享 90"
        )));

        String text = text(result);
        assertFalse(result.isError());
        assertEquals(1, detailRequestCount.get());
        assertTrue(text.contains("商品详情补取状态: 成功"));
        assertTrue(text.contains("详情页证据级别: 华为商城商品详情页公开的服务端渲染结构化数据"));
        assertTrue(text.contains("页面商品: 华为畅享 90"));
        assertTrue(text.contains("prdId=10086174757473"));
        assertTrue(text.contains("sbomCode=2601010613207"));
        assertTrue(text.contains("页面标价=1299 元"));
        assertTrue(text.contains("页面标价=1599 元"));
        assertTrue(text.contains("页面原价=1699 元"));
        assertTrue(text.contains("分期：至高可享 6 期 0 分期利息"));
        assertTrue(text.contains("已忽略 1 条"));
        assertFalse(text.contains("Pura 90 Pro Max"));
        assertFalse(text.contains("6499"));
    }

    @Test
    @DisplayName("相似系列但不同型号不得被当作目标商品或输出其价格")
    void priceStockRejectsSimilarProductVariant() {
        responseBody = """
                {"results":{"web":[{
                  "url":"https://www.vmall.com/product/comdetail/index.html?prdId=10086454848882",
                  "title":"HUAWEI Pura 90 Pro Max 12GB+256GB",
                  "description":"页面价格 ¥6499"
                }]}}
                """;

        CallToolResult result = executor("k").handleCall(request(Map.of(
                "query", "HUAWEI Pura 90价格",
                "search_type", "price_stock",
                "product_name", "HUAWEI Pura 90"
        )));

        String text = text(result);
        assertFalse(result.isError());
        assertEquals(0, detailRequestCount.get());
        assertTrue(text.contains("没有与目标商品严格匹配"));
        assertTrue(text.contains("已忽略 1 条"));
        assertFalse(text.contains("6499"));
    }

    @Test
    @DisplayName("You.com 短暂返回 503 时重试一次")
    void retriesTransientSearchFailure() {
        searchFailuresBeforeSuccess = 1;

        CallToolResult result = executor("k").handleCall(request(Map.of("query", "华为手机")));

        assertFalse(result.isError());
        assertEquals(2, searchRequestCount.get());
        assertTrue(text(result).contains("检索结果数"));
    }

    @Test
    @DisplayName("参数与兼容查询扩展到华为消费者业务官网并过滤其他站点")
    void compatibilitySearchUsesOfficialHuaweiDomains() {
        responseBody = """
                {"results":{"web":[
                  {"url":"https://consumer.huawei.com/cn/support/content/zh-cn123/","title":"连接指南","description":"官方支持说明"},
                  {"url":"https://example.com/fake","title":"站外页面","description":"不应出现"}
                ]}}
                """;

        CallToolResult result = executor("k").handleCall(request(Map.of(
                "query", "Sound X5 怎么连接手机",
                "search_type", "compatibility_support",
                "product_name", "Sound X5"
        )));

        assertFalse(result.isError());
        assertEquals("vmall.com,consumer.huawei.com", lastQueryParams.get().get("include_domains"));
        assertTrue(text(result).contains("来源类型: 华为官方支持页"));
        assertTrue(text(result).contains("consumer.huawei.com"));
        assertFalse(text(result).contains("example.com"));
    }

    @Test
    @DisplayName("API 返回非 200 时返回 errorResult 且不抛异常")
    void httpErrorReturnsErrorResult() {
        responseCode = 500;
        CallToolResult result = executor("k").handleCall(request(Map.of("query", "t")));

        assertTrue(result.isError());
        assertTrue(text(result).contains("500"));
    }

    @Test
    @DisplayName("空结果集返回友好提示文本")
    void emptyResults() {
        responseBody = "{\"results\": {}}";
        CallToolResult result = executor("k").handleCall(request(Map.of("query", "t")));

        assertFalse(result.isError());
        assertTrue(text(result).contains("未检索到相关官方网页结果"));
    }

    @Test
    @DisplayName("count 截断：合并 web+news 后按 count 截断，仅返回 count 条")
    void countCapsTotalResults() {
        // SAMPLE_BODY 共 3 条（2 web + 1 news），count=2 截断为 2 条，保留靠前的两条 web
        CallToolResult result = executor("test-key").handleCall(
                request(Map.of("query", "t", "count", 2)));

        String text = text(result);
        assertFalse(result.isError());
        assertTrue(text.contains("检索结果数: 2"));
        assertTrue(text.contains("1. 网页结果A"));
        assertTrue(text.contains("2. 网页结果B"));
        assertFalse(text.contains("3. "), "count=2 时不应出现第 3 条");
    }

    // ============== helpers ==============

    /**
     * 构造 executor：指向本地 stub，并固定环境变量读取结果，屏蔽真实运行环境
     */
    private YouComSearchMcpExecutor executor(String envKey) {
        YouComSearchMcpExecutor executor = new YouComSearchMcpExecutor() {
            @Override
            protected String readEnv(String name) {
                return envKey;
            }
        };
        executor.apiUrl = stubUrl;
        executor.productDetailClient.detailUrl = stubBaseUrl + "/product/comdetail/index.html";
        return executor;
    }

    private CallToolRequest request(Map<String, Object> args) {
        return new CallToolRequest("youcom_search", args);
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                params.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }
}
