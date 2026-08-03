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

package com.nageoffer.ai.ragent.rag.core.vector.strategy;

import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.vector.VectorRetrieverService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParallelQueryVectorReuseTest {

    @Test
    @DisplayName("多个意图并行检索时复用同一个Query向量")
    void multipleIntentsReuseOneQueryVector() {
        VectorRetrieverService retrieverService = mock(VectorRetrieverService.class);
        float[] normalizedVector = new float[]{0.6F, 0.8F};
        when(retrieverService.embedAndNormalize("如何申请？")).thenReturn(normalizedVector);
        when(retrieverService.retrieveByVector(any(float[].class), any(RetrieveRequest.class))).thenReturn(List.of());

        NodeScore first = NodeScore.builder()
                .node(IntentNode.builder()
                        .id("finance")
                        .name("财务")
                        .collectionNames(List.of("kb-finance"))
                        .build())
                .score(0.95)
                .build();
        NodeScore second = NodeScore.builder()
                .node(IntentNode.builder()
                        .id("policy")
                        .name("制度")
                        .collectionNames(List.of("kb-policy"))
                        .build())
                .score(0.9)
                .build();

        IntentParallelRetriever retriever = new IntentParallelRetriever(retrieverService, Runnable::run);
        retriever.retrieveByIntents("如何申请？", List.of(first, second), 20);

        ArgumentCaptor<float[]> vectorCaptor = ArgumentCaptor.forClass(float[].class);
        verify(retrieverService, times(2)).retrieveByVector(vectorCaptor.capture(), any(RetrieveRequest.class));
        assertArrayEquals(normalizedVector, vectorCaptor.getAllValues().get(0));
        assertArrayEquals(normalizedVector, vectorCaptor.getAllValues().get(1));
        assertSame(normalizedVector, vectorCaptor.getAllValues().get(0));
        assertSame(normalizedVector, vectorCaptor.getAllValues().get(1));
        verify(retrieverService, times(1)).embedAndNormalize("如何申请？");
        verify(retrieverService, never()).retrieve(any(RetrieveRequest.class));
    }
}
