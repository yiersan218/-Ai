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

package com.nageoffer.ai.ragent.rag.core.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.convention.SourceRef;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourcesAssemblerTest {

    @Test
    void usesFrontmatterCanonicalUrlForUploadedMarkdown() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .id("doc-1")
                .docName("watch.md")
                .sourceType("file")
                .fileType("md")
                .metadata("{\"source_urls\":[\"https://www.vmall.com/watch\"],"
                        + "\"canonical_url\":\"https://consumer.huawei.com/watch\"}")
                .build();
        when(mapper.selectBatchIds(anyList())).thenReturn(List.of(document));
        SourcesAssembler assembler = new SourcesAssembler(mapper, new ObjectMapper());

        RetrievedChunk chunk = RetrievedChunk.builder()
                .id("chunk-1")
                .docId("doc-1")
                .docName("watch.md")
                .text("稳定卖点")
                .score(0.9F)
                .build();
        List<SourceRef> sources = assembler.assemble(Map.of("kb-product-parameters", List.of(chunk)));

        assertEquals(1, sources.size());
        assertEquals("https://consumer.huawei.com/watch", sources.get(0).getUrl());
    }
}
