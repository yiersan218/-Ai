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

package com.nageoffer.ai.ragent.core.parser.model;

import java.util.List;

/**
 * 标题 Block：在 ChunkerNode 中由 HeadingHandler 消费，不直接产 chunk，而是累积到后续 chunk 的 outlinePath
 *
 * @param level markdown 标题级别，1-6
 * @param text  标题文本
 */
public record HeadingBlock(
        String id,
        Provenance provenance,
        List<String> outlinePath,
        int level,
        String text
) implements Block {
}
