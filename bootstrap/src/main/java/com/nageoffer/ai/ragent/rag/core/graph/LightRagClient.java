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

package com.nageoffer.ai.ragent.rag.core.graph;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.GraphProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LightRAG 微服务 HTTP 客户端
 * <p>
 * 封装对 LightRAG server（默认 :9621）的调用：检索取上下文、文档写入
 * 仅当 rag.graph.type=lightrag 时注册；任何调用失败都降级（检索返回空、写入记 warn），绝不阻断主链路
 * <p>
 * 重要：LightRAG /query 无 per-request workspace 参数——workspace 为实例级（由服务端 env 固定）
 * 故单实例即单图；按 KB 隔离子图需多实例，或在结果侧按 file_path 归属过滤，均属后续阶段
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.graph", name = "type", havingValue = "lightrag")
public class LightRagClient {

    private static final MediaType JSON = MediaType.parse("application/json");

    /**
     * file_path 中归属 docId 的匹配模式
     * <p>
     * 写入时 file_source 编码为 {collectionName}_{docId}，docId 为雪花纯数字；取 file_path 中最后一段连续数字为归属 docId，
     * 兼容服务端可能对 file_path 追加的扩展名 / 路径修饰
     */
    private static final Pattern DOC_ID_PATTERN = Pattern.compile("(\\d+)");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GraphProperties properties;

    public LightRagClient(@Qualifier("syncHttpClient") OkHttpClient httpClient,
                          ObjectMapper objectMapper,
                          GraphProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 检索图谱上下文，返回命中的来源证据（引用）：全局视图、不按库过滤
     *
     * @param question 查询问题
     * @param mode     LightRAG 查询模式 naive / local / global / hybrid / mix
     * @param topK     期望候选数
     */
    public List<RetrievedChunk> retrieve(String question, String mode, int topK) {
        return retrieve(question, mode, topK, List.of());
    }

    /**
     * 检索图谱上下文，并按 collections 过滤到命中库（结果侧「意图定向」）
     * <p>
     * only_need_context=true 只取证据、不让 LightRAG 生成答案；证据回到主链路，
     * 由既有融合 / Rerank / 上下文组装统一处理，与其它通道一视同仁
     * <p>
     * LightRAG /query 无 per-request workspace，此处查全局图后在 Java 侧按 file_path 归属过滤到命中库，
     * 与向量 / 关键词的「意图域」语义对齐；collections 为空表示不过滤（全局视图）
     *
     * @param question    查询问题
     * @param mode        LightRAG 查询模式 naive / local / global / hybrid / mix
     * @param topK        期望候选数
     * @param collections 目标知识库 collection 名，空则不过滤
     */
    public List<RetrievedChunk> retrieve(String question, String mode, int topK, Collection<String> collections) {
        if (StrUtil.isBlank(question)) {
            return List.of();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("query", question);
            body.put("mode", StrUtil.isNotBlank(mode) ? mode : "mix");
            body.put("only_need_context", true);
            body.put("include_references", true);
            body.put("include_chunk_content", true);
            if (topK > 0) {
                body.put("top_k", topK);
            }
            JsonNode root = post("/query", body);
            return root != null ? parseReferences(root, collections) : List.of();
        } catch (Exception e) {
            log.warn("LightRAG 检索失败，降级为空结果: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 拉取图谱子图，供后台可视化用（只读，与 retrieve 的证据召回相互独立）
     * <p>
     * label 为起点实体名，传 "*" 取全图；服务端按「到起点跳数 + 节点度数」优先级在 maxNodes 处截断，
     * 返回原始 {@code {nodes,edges,is_truncated}} 结构，交由上层映射为前端视图；失败降级 null
     *
     * @param label    起点实体名，"*" 表示全图
     * @param maxDepth 子图最大深度
     * @param maxNodes 最大节点数（服务端上限 1000）
     */
    public JsonNode fetchGraph(String label, int maxDepth, int maxNodes) {
        try {
            HttpUrl base = HttpUrl.parse(url("/graphs"));
            if (base == null) {
                return null;
            }
            HttpUrl target = base.newBuilder()
                    .addQueryParameter("label", StrUtil.isNotBlank(label) ? label : "*")
                    .addQueryParameter("max_depth", String.valueOf(Math.max(1, maxDepth)))
                    .addQueryParameter("max_nodes", String.valueOf(Math.max(1, maxNodes)))
                    .build();
            return execute(auth(new Request.Builder().url(target).get()), "/graphs");
        } catch (Exception e) {
            log.warn("LightRAG 图谱拉取失败 label={}: {}", label, e.getMessage());
            return null;
        }
    }

    /**
     * 检索实体标签，供可视化的实体搜索框用
     * <p>
     * keyword 为空取热门标签（作为进入图谱的默认入口），否则按关键字模糊搜索；防御式兼容返回纯字符串或对象元素
     *
     * @param keyword 关键字，空则取热门
     * @param limit   返回上限
     */
    public List<String> fetchLabels(String keyword, int limit) {
        try {
            boolean popular = StrUtil.isBlank(keyword);
            HttpUrl base = HttpUrl.parse(url(popular ? "/graph/label/popular" : "/graph/label/search"));
            if (base == null) {
                return List.of();
            }
            HttpUrl.Builder builder = base.newBuilder();
            if (popular) {
                builder.addQueryParameter("limit", String.valueOf(clamp(limit, 300, 1000)));
            } else {
                builder.addQueryParameter("q", keyword)
                        .addQueryParameter("limit", String.valueOf(clamp(limit, 50, 100)));
            }
            JsonNode root = execute(auth(new Request.Builder().url(builder.build()).get()), "labels");
            List<String> labels = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    String value = node.isTextual()
                            ? node.asText("")
                            : node.path("label").asText(node.path("name").asText(""));
                    if (StrUtil.isNotBlank(value)) {
                        labels.add(value);
                    }
                }
            }
            return labels;
        } catch (Exception e) {
            log.warn("LightRAG 标签检索失败 keyword={}: {}", keyword, e.getMessage());
            return List.of();
        }
    }

    /**
     * 取值兜底并封顶：非正值回退 fallback，超出 max 截到 max
     */
    private int clamp(int value, int fallback, int max) {
        int v = value > 0 ? value : fallback;
        return Math.min(v, max);
    }

    /**
     * 写入 / 更新一篇文档到图谱
     * <p>
     * file_source 编码来源标识（如 docId），供检索时按 file_path 回溯文档归属
     *
     * @param text       文档全文
     * @param fileSource 来源标识（建议传 docId）
     */
    public void insertText(String text, String fileSource) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("text", text);
            if (StrUtil.isNotBlank(fileSource)) {
                body.put("file_source", fileSource);
            }
            post("/documents/text", body);
        } catch (Exception e) {
            log.warn("LightRAG 文档写入失败 file_source={}: {}", fileSource, e.getMessage());
        }
    }

    /**
     * 删除某文档的图谱数据（按 docId 匹配 file_path）
     * <p>
     * docId 全局唯一（雪花），作为 token 落在 file_path 中，不受服务端 basename 归一化影响，匹配稳
     */
    public void deleteByDoc(String docId) {
        if (StrUtil.isBlank(docId)) {
            return;
        }
        deleteMatching(filePath -> filePath.contains(docId), "docId=" + docId);
    }

    /**
     * 删除某知识库的全部图谱数据（按 collectionName 前缀 token 匹配）
     * <p>
     * file_source 编码为 {collectionName}_{docId}，故按 "{collectionName}_" token 命中该库全部文档
     */
    public void deleteByCollection(String collectionName) {
        if (StrUtil.isBlank(collectionName)) {
            return;
        }
        String token = collectionName + "_";
        deleteMatching(filePath -> filePath.contains(token), "collection=" + collectionName);
    }

    /**
     * 列举文档、按 file_path 谓词匹配出 LightRAG doc_id 后批量删除
     * <p>
     * LightRAG 删除按其内部 doc_id（内容派生），故先 GET /documents 反查、再 DELETE /documents/delete_document；
     * 全量列举后在内存匹配，语料很大时可改用 /documents/paginated 过滤。best-effort，任一步异常只记 warn
     */
    private void deleteMatching(Predicate<String> filePathMatch, String logKey) {
        try {
            JsonNode docs = get("/documents");
            if (docs == null) {
                return;
            }
            List<String> docIds = new ArrayList<>();
            JsonNode statuses = docs.path("statuses");
            if (statuses.isObject()) {
                statuses.forEach(group -> {
                    if (group.isArray()) {
                        for (JsonNode d : group) {
                            String filePath = d.path("file_path").asText("");
                            if (StrUtil.isNotBlank(filePath) && filePathMatch.test(filePath)) {
                                String id = d.path("id").asText("");
                                if (StrUtil.isNotBlank(id)) {
                                    docIds.add(id);
                                }
                            }
                        }
                    }
                });
            }
            if (docIds.isEmpty()) {
                return;
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.set("doc_ids", objectMapper.valueToTree(docIds));
            delete("/documents/delete_document", body);
        } catch (Exception e) {
            log.warn("LightRAG 文档删除失败 {}: {}", logKey, e.getMessage());
        }
    }

    private JsonNode post(String path, JsonNode body) throws Exception {
        return execute(auth(new Request.Builder().url(url(path))
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))), path);
    }

    private JsonNode get(String path) throws Exception {
        return execute(auth(new Request.Builder().url(url(path)).get()), path);
    }

    private JsonNode delete(String path, JsonNode body) throws Exception {
        return execute(auth(new Request.Builder().url(url(path))
                .delete(RequestBody.create(objectMapper.writeValueAsString(body), JSON))), path);
    }

    private String url(String path) {
        return StrUtil.removeSuffix(properties.getLightrag().getBaseUrl(), "/") + path;
    }

    /**
     * 本地部署默认无 Key，仅在显式配置时附带鉴权头
     */
    private Request.Builder auth(Request.Builder builder) {
        String apiKey = properties.getLightrag().getApiKey();
        if (StrUtil.isNotBlank(apiKey)) {
            builder.header("X-API-Key", apiKey);
        }
        return builder;
    }

    /**
     * 统一执行：按超时新建 client，非 2xx / 空响应返回 null，异常向上抛由调用方降级
     */
    private JsonNode execute(Request.Builder builder, String path) throws Exception {
        int timeoutMs = Math.max(1000, properties.getLightrag().getTimeoutMs());
        OkHttpClient client = httpClient.newBuilder()
                .callTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .build();
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                log.warn("LightRAG 请求失败 path={}, code={}", path, response.code());
                return null;
            }
            String bodyStr = response.body() != null ? response.body().string() : "";
            return StrUtil.isNotBlank(bodyStr) ? objectMapper.readTree(bodyStr) : null;
        }
    }

    /**
     * 解析 /query 响应的 references 为 RetrievedChunk
     * <p>
     * 结构 {@code {"response":"...","references":[{"reference_id","file_path","content":[...]}]}}；
     * references 缺失或为空时回退：把 response 上下文整体作为一个证据块，防御式读取
     * <p>
     * collections 非空时按 file_path 归属过滤到命中库；此时 response 兜底块无 file_path、无法归属，故一并跳过
     */
    private List<RetrievedChunk> parseReferences(JsonNode root, Collection<String> collections) {
        boolean filterByCollection = collections != null && !collections.isEmpty();
        List<RetrievedChunk> chunks = new ArrayList<>();
        JsonNode references = root.path("references");
        if (references.isArray() && !references.isEmpty()) {
            int rank = 0;
            for (JsonNode ref : references) {
                String refId = ref.path("reference_id").asText("");
                String filePath = ref.path("file_path").asText("");
                // 结果侧按命中库过滤：不属于任一目标 collection 的证据丢弃（丢弃项不占名次，保持保留项名次连续）
                if (filterByCollection && !matchesCollection(filePath, collections)) {
                    continue;
                }
                StringBuilder text = new StringBuilder();
                JsonNode content = ref.path("content");
                if (content.isArray()) {
                    for (JsonNode c : content) {
                        String s = c.asText("");
                        if (StrUtil.isNotBlank(s)) {
                            text.append(s).append('\n');
                        }
                    }
                }
                String body = text.toString().trim();
                if (StrUtil.isBlank(body)) {
                    body = filePath;
                }
                if (StrUtil.isBlank(body)) {
                    continue;
                }
                // 从 file_path({collectionName}_{docId}) 解析归属 docId，让图谱证据与向量证据在文档层面对齐：
                // 末端富化据此按 docId 补真实标题，并与同源向量证据聚合进同一文档块
                String docId = parseDocId(filePath);
                // 分数取按名次递减的中性分数 1/(rank+1)：无量纲，仅表达通道内相对顺序，
                // 多通道时由 FusionPostProcessor(RRF) 重算，开启 Rerank 时由精排模型覆盖
                chunks.add(RetrievedChunk.builder()
                        .id(StrUtil.isNotBlank(refId) ? refId : "graph:" + rank)
                        .text(body)
                        .score(1.0f / (rank + 1))
                        .docId(docId)
                        // docId 解析到则留空 docName、交富化按 docId 补真实标题；解析不到才回退 file_path 以免完全无来源
                        .docName(docId != null ? null : (StrUtil.isNotBlank(filePath) ? filePath : null))
                        .build());
                rank++;
            }
            return chunks;
        }
        // 回退：references 关闭或为空时，用 response 上下文兜底为单个证据块
        // 过滤生效时该兜底块无 file_path、无法归属命中库，跳过以免破坏「意图定向」语义
        if (!filterByCollection) {
            String context = root.path("response").asText("");
            if (StrUtil.isNotBlank(context)) {
                chunks.add(RetrievedChunk.builder()
                        .id("graph:context")
                        .text(context)
                        .score(1.0f)
                        .build());
            }
        }
        return chunks;
    }

    /**
     * file_path 是否命中给定任一 collection
     * <p>
     * 按 {collectionName}_ 前缀 token 判断，与 deleteByCollection 同一 token 语义，抗服务端 file_path 归一化
     */
    private boolean matchesCollection(String filePath, Collection<String> collections) {
        if (StrUtil.isBlank(filePath)) {
            return false;
        }
        for (String collectionName : collections) {
            if (StrUtil.isNotBlank(collectionName) && filePath.contains(collectionName + "_")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 file_path 解析归属 docId
     * <p>
     * 取最后一段连续数字：docId 为雪花纯数字且是 {collectionName}_{docId} 的末段，故为 file_path 中最后一个数字串；
     * 无数字则返回 null（图谱证据退化为无 docId，仅影响文档聚合、不影响召回）
     */
    private String parseDocId(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return null;
        }
        Matcher matcher = DOC_ID_PATTERN.matcher(filePath);
        String docId = null;
        while (matcher.find()) {
            docId = matcher.group(1);
        }
        return docId;
    }
}
