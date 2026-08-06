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

package com.nageoffer.ai.ragent.rag.core.intent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IntentNodeJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void roundTripsIntentTreeWithoutSerializingComputedCollections() throws Exception {
        List<IntentNode> roots = IntentTreeFactory.buildIntentTree();

        String json = objectMapper.writeValueAsString(roots);
        List<IntentNode> restored = objectMapper.readValue(json, new TypeReference<>() {
        });

        assertFalse(json.contains("\"effectiveCollectionNames\""));
        IntentNode productParameters = find(restored, "kb-product-parameters");
        assertNotNull(productParameters);
        assertEquals(List.of("hwmallproduct"), productParameters.getCollectionNames());
        assertEquals(List.of("hwmallproduct"), productParameters.getEffectiveCollectionNames());
    }

    @Test
    void ignoresComputedCollectionsAlreadyPresentInLegacyCacheJson() throws Exception {
        String legacyJson = """
                [{
                  "id": "root",
                  "name": "root",
                  "level": "DOMAIN",
                  "kind": "KB",
                  "collectionNames": [],
                  "effectiveCollectionNames": [],
                  "children": [{
                    "id": "leaf",
                    "name": "leaf",
                    "level": "TOPIC",
                    "kind": "KB",
                    "collectionNames": ["knowledge"],
                    "effectiveCollectionNames": ["knowledge"],
                    "children": []
                  }]
                }]
                """;

        List<IntentNode> restored = objectMapper.readValue(legacyJson, new TypeReference<>() {
        });

        IntentNode leaf = find(restored, "leaf");
        assertNotNull(leaf);
        assertEquals(List.of("knowledge"), leaf.getEffectiveCollectionNames());
    }

    private static IntentNode find(List<IntentNode> nodes, String id) {
        for (IntentNode node : nodes) {
            if (id.equals(node.getId())) {
                return node;
            }
            IntentNode found = find(node.getChildren(), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
