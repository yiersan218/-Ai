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

package com.nageoffer.ai.ragent.rag.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 检索配置
 * <p>
 * 检索漏斗有三段各自独立的预算（见 {@code RetrievalBudget}）：
 * 召回扇出 {@link #recallBudget} → Rerank 候选池上限 {@link Fusion#rerankCandidateLimit} → 最终条数 {@link #defaultTopK}，
 * 三者须单调收窄，启动时由 {@link #afterPropertiesSet()} 校验
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.search")
public class SearchChannelProperties implements InitializingBean {

    /**
     * 默认最终进 LLM 的条数（检索预算的 contextTopK 段）
     * 即产品语义的 topK；请求可覆盖，未覆盖时用此值
     */
    private int defaultTopK = 10;

    /**
     * 每通道召回条数（检索预算的 recallBudget 段）
     * 各通道（向量意图路 / 关键词 / 图谱）统一按此绝对值召回候选，交由下游 RRF+Rerank 收窄
     * 须 ≥ defaultTopK（漏斗单调，启动校验）；<=0 时回退 defaultTopK 作兜底守卫
     */
    private int recallBudget = 20;

    /**
     * 检索通道配置
     */
    private Channels channels = new Channels();

    /**
     * 多通道结果融合配置
     */
    private Fusion fusion = new Fusion();

    /**
     * 解析召回扇出基数：优先使用显式 recallBudget，未配置（<=0）时回退到最终条数
     */
    public int resolveRecallBudget(int contextTopK) {
        return recallBudget > 0 ? recallBudget : contextTopK;
    }

    /**
     * 校验检索预算的漏斗单调不变式：recallBudget ≥ contextTopK 且 candidateLimit ≥ contextTopK
     * 违反意味着「召回还没最终条数多」或「送进 Rerank 的候选还没最终条数多」，Rerank 无从产出足量结果，
     * 属配置矛盾，启动即失败胜过线上悄悄少召回
     */
    @Override
    public void afterPropertiesSet() {
        int contextTopK = defaultTopK;
        if (contextTopK <= 0) {
            throw new IllegalStateException("rag.search.default-top-k 必须为正数，当前：" + contextTopK);
        }
        int resolvedRecall = resolveRecallBudget(contextTopK);
        if (resolvedRecall < contextTopK) {
            throw new IllegalStateException(String.format(
                    "检索预算漏斗不变式被破坏：recallBudget(%d) < contextTopK(%d)，召回扇出不得小于最终条数，"
                            + "请调大 rag.search.recall-budget 或调小 rag.search.default-top-k",
                    resolvedRecall, contextTopK));
        }
        int candidateLimit = fusion.getRerankCandidateLimit();
        if (candidateLimit > 0 && candidateLimit < contextTopK) {
            throw new IllegalStateException(String.format(
                    "检索预算漏斗不变式被破坏：candidateLimit(%d) < contextTopK(%d)，送入 Rerank 的候选池不得小于最终条数，"
                            + "请调大 rag.search.fusion.rerank-candidate-limit 或调小 rag.search.default-top-k",
                    candidateLimit, contextTopK));
        }
    }

    @Data
    public static class Channels {

        /**
         * 向量检索配置
         * 一条向量通道，按 KB 意图置信度在通道内二选一作用域，意图定向与全局子配置各管一路
         */
        private Vector vector = new Vector();

        /**
         * 关键词检索配置
         */
        private Keyword keyword = new Keyword();

        /**
         * 联网检索配置（You.com Search）
         */
        private WebSearch webSearch = new WebSearch();

        /**
         * 知识图谱检索配置
         */
        private Graph graph = new Graph();
    }

    @Data
    public static class Vector {

        /**
         * 是否启用
         * 一条向量通道一个总开关；关闭即全站无向量召回
         */
        private boolean enabled = true;

        /**
         * 意图定向子配置
         * 有足够置信的 KB 意图时，收窄到命中库检索这一路的参数
         */
        private IntentDirected intentDirected = new IntentDirected();

        /**
         * 全局子配置
         * 无 / 低置信 KB 意图时，退化为全库检索这一路的参数
         */
        private Global global = new Global();
    }

    @Data
    public static class IntentDirected {

        /**
         * 最低意图分数
         * 低于此分数的意图节点会被过滤，不参与「是否收窄作用域」的判定
         */
        private double minIntentScore = 0.4;
    }

    @Data
    public static class Global {

        /**
         * 意图置信度阈值
         * KB 意图最高分低于此阈值时，通道退化为全库检索
         */
        private double confidenceThreshold = 0.6;

        /**
         * 单意图补充检索阈值
         * 仅识别出一个 KB 意图且分数低于此阈值时，通道退化为全库检索作为安全网
         */
        private double singleIntentSupplementThreshold = 0.8;

        /**
         * 全局检索候选预算
         * 全局作用域取数的旋钮：单次跨库查询的 LIMIT 上限（fan-out 兜底路径下为每库上限）
         * <=0 时回退到 Rerank 候选池上限 rerankCandidateLimit：全局路召回超过候选池上限的部分下游必被截断、属空转，
         * 故默认让它跟随候选池上限（单一真源）；确需更宽的跨库广度时填正值独立覆盖
         */
        private int candidateBudget = 0;

        /**
         * 解析全局检索候选预算
         * 优先使用绝对预算 candidateBudget；未配置（<=0）时回退到传入的 Rerank 候选池上限
         */
        public int resolveCandidateBudget(int candidateLimitFallback) {
            return candidateBudget > 0 ? candidateBudget : candidateLimitFallback;
        }
    }

    @Data
    public static class Keyword {

        /**
         * 是否启用
         * 仅当 rag.keyword.type != none（存在关键词检索实现）时才会真正生效
         */
        private boolean enabled = false;
    }

    @Data
    public static class Graph {

        /**
         * 是否启用
         * 仅当开启图谱后端（rag.graph.type != none）时才会真正生效
         */
        private boolean enabled = false;
    }

    @Data
    public static class WebSearch {

        /**
         * 是否启用
         * 默认关闭；开启后还需配置 api-key（或环境变量 YDC_API_KEY），两者缺一通道不生效
         */
        private boolean enabled = false;

        /**
         * 最多返回的结果条数（网页 + 新闻合计）
         * 默认 5，上限 20；向 You.com 传的是「每 section」数量，合并后由通道统一截断到此值
         */
        private int count = 5;

        /**
         * 请求超时（秒）
         */
        private int timeoutSeconds = 10;

        /**
         * You.com Search API Key
         * 建议留空，此时回退读取环境变量 YDC_API_KEY，避免密钥落入配置文件
         */
        private String apiKey = "";

        /**
         * You.com Search API 地址
         * 一般无需修改，测试时可指向本地 stub
         */
        private String apiUrl = "https://ydc-index.io/v1/search";
    }

    @Data
    public static class Fusion {

        /**
         * 融合策略
         * rrf 倒数名次融合（当前唯一实现），off 关闭融合直接透传
         */
        private String strategy = "rrf";

        /**
         * RRF 平滑常数 k
         * 值越大越弱化高名次的优势。经典取 60（面向上千候选的检索场景），
         * 但本链路每通道候选通常仅约 20~40 条，k=60 会把名次差异过度抹平（头部与尾部分数几乎拉不开），
         * 建议按候选池量级调低（如 20）让头部更有区分度；具体值配合检索归因日志校准
         */
        private int rrfK = 60;

        /**
         * Rerank 候选上限
         * RRF 融合排序后仅保留前 N 个高分候选送入 Rerank 精排，
         * 既控制 Rerank 的成本与延迟，又让多路命中的候选凭 RRF 分数优先入选
         * <=0 表示不截断（全量送入 Rerank），行业经验值 40~100
         */
        private int rerankCandidateLimit = 50;

        /**
         * 各通道 RRF 贡献权重
         * 让不同可信度的通道在融合时话语权不同：RRF 只用名次、丢弃分数量纲，无权重时各通道等权，
         * 一个新接入 / 噪声较多的通道会与最可信通道在每个名次上平起平坐。加权后 delta = 权重 / (k + rank)
         */
        private ChannelWeights channelWeights = new ChannelWeights();
    }

    @Data
    public static class ChannelWeights {

        /**
         * 向量权重
         * 向量模态最可信；意图定向与全局同属这一条通道，共用一个权重
         */
        private double vector = 1.0;

        /**
         * 关键词（BM25）权重
         */
        private double keyword = 1.0;

        /**
         * 图谱权重
         * 图谱为新接入通道、跑在单一全局图上、证据仅经结果侧过滤，默认降权，
         * 待归因日志验证其 Rerank 存活率后再上调；存活率长期为 0 说明当前是纯成本
         */
        private double graph = 0.5;

        /**
         * 联网检索权重
         */
        private double webSearch = 0.5;

        /**
         * 未显式配置通道的兜底权重
         */
        private double defaultWeight = 1.0;
    }
}
