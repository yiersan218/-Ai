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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IntentTreeFactoryTest {

    @Test
    void buildsHuaweiMallTreeWithSevenKnowledgeCollections() {
        List<IntentNode> allNodes = flatten(IntentTreeFactory.buildIntentTree());

        assertEquals(21, allNodes.size());
        assertEquals(15, allNodes.stream().filter(IntentNode::isLeaf).count());
        assertEquals(List.of("hwmallproduct"), find(allNodes, "kb-product-parameters").getEffectiveCollectionNames());
        assertEquals(List.of("hwmallreturn"), find(allNodes, "kb-return-refund").getEffectiveCollectionNames());
        assertEquals(List.of("hwmallwarranty"), find(allNodes, "kb-warranty-repair").getEffectiveCollectionNames());
        assertEquals(List.of("hwmallinvoice"), find(allNodes, "kb-invoice").getEffectiveCollectionNames());
        assertEquals("商城动态信息", find(allNodes, "hwmall-current-info").getName());
        assertEquals("youcom_search", find(allNodes, "mcp-price-stock").getMcpToolId());
    }

    private static IntentNode find(List<IntentNode> nodes, String id) {
        IntentNode node = nodes.stream().filter(each -> id.equals(each.getId())).findFirst().orElse(null);
        assertNotNull(node, "missing node " + id);
        return node;
    }

    private static List<IntentNode> flatten(List<IntentNode> roots) {
        List<IntentNode> nodes = new ArrayList<>();
        for (IntentNode root : roots) {
            nodes.add(root);
            nodes.addAll(flatten(root.getChildren()));
        }
        return nodes;
    }
}
