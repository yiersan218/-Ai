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

package com.nageoffer.ai.ragent.infra.chat;

import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.enums.Tier;

/**
 * 通用大语言模型（LLM）访问接口
 * <p>
 * 用途说明：
 * - 为业务层提供统一的大模型访问能力，屏蔽不同厂商/协议的差异
 * - 支持同步调用（一次性返回完整回答）与流式调用（按 token/片段增量输出）
 * - 可通过不同实现类适配各模型平台，如：
 * - 本地推理（Ollama、LM Studio 等）
 * - 阿里云百炼（DashScope）
 * - DeepSeek / OpenAI / Qwen API
 * - 企业内部推理服务
 * <p>
 * 核心能力：
 * - 标准化 Prompt 构造（system / user / context）
 * - RAG 场景支持（可传入检索到的上下文）
 * - 参数化控制（温度、top_p、max_tokens、stop 等）
 * - 流式 token 输出（配合 StreamCallback）
 * <p>
 * 注意事项：
 * - 档位默认 standard；调用点想要更快/更强模型时传 Tier 覆盖，深度思考（thinking=true）走 deep-thinking-tier
 * - 复杂场景（带上下文、多轮对话、控制生成参数）通过 ChatRequest 表达
 * - 流式模式下需正确处理 cancel()，并确保资源释放
 */
public interface LLMService {

    /**
     * 同步调用（默认档位）
     * <p>
     * 说明：
     * - 档位默认 standard（未传 Tier 覆盖）；深度思考由 request.thinking 表达，命中时走 deep-thinking-tier
     * - 支持系统提示词、消息列表、生成参数等精细控制
     *
     * @param request ChatRequest 包含完整配置的请求对象
     * @return 模型返回的完整回答
     */
    String chat(ChatRequest request);

    /**
     * 同步调用（指定档位覆盖）
     * <p>
     * 说明：
     * - tier 显式指定档位（想要更快/更强模型时由调用点传入），覆盖默认 standard
     * - 深度思考仍优先：request.thinking=true 时走 deep-thinking-tier
     *
     * @param request ChatRequest 完整配置的请求
     * @param tier    目标档位
     * @return 模型返回的完整回答
     */
    String chat(ChatRequest request, Tier tier);

    /**
     * 同步调用（指定档位覆盖 + 指定优先模型）
     * <p>
     * 说明（preferred 语义）：
     * - 优先使用 preferredModelId 指定的模型，失败后回退到 tier 档位的其余候选
     * - preferredModelId 为空时等同于 chat(request, tier)
     *
     * @param request          ChatRequest 完整配置的请求
     * @param tier             回退档位
     * @param preferredModelId 优先模型 id，为空时走档位候选
     * @return 模型返回的完整回答
     */
    String chat(ChatRequest request, Tier tier, String preferredModelId);

    /**
     * 流式调用（默认档位）
     * <p>
     * 说明：
     * - 档位默认 standard；深度思考由 request.thinking 表达，命中时走 deep-thinking-tier
     * - 所有增量内容通过 callback.onContent() 回调，结束调用 onComplete()，异常调用 onError()
     *
     * @param request  ChatRequest 完整配置的请求
     * @param callback 流式回调接口
     * @return StreamCancellationHandle 用于取消推理
     */
    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback);
}
