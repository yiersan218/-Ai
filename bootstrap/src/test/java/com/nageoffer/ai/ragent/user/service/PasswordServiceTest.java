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

package com.nageoffer.ai.ragent.user.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void shouldEncodeAndMatchPassword() {
        String encoded = passwordService.encode("secure123");

        assertNotEquals("secure123", encoded);
        assertTrue(passwordService.isEncoded(encoded));
        assertTrue(passwordService.matches("secure123", encoded));
        assertFalse(passwordService.matches("wrong123", encoded));
    }

    @Test
    void shouldMatchLegacyPlaintextPasswordForMigration() {
        assertTrue(passwordService.matches("legacy123", "legacy123"));
        assertFalse(passwordService.matches("legacy123", "other123"));
        assertFalse(passwordService.isEncoded("legacy123"));
    }
}
