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

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.config.RegistrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationRateLimiterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private RegistrationRateLimiter registrationRateLimiter;

    @BeforeEach
    void setUp() {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setWindowSeconds(600);
        properties.setRequestLimit(20);
        properties.setSuccessLimit(3);
        registrationRateLimiter = new RegistrationRateLimiter(stringRedisTemplate, properties);
    }

    @Test
    void shouldAllowFirstTwentyRequestsAndRejectNextRequest() {
        doReturn(20L, 21L).when(stringRedisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertDoesNotThrow(() -> registrationRateLimiter.recordRequest("192.0.2.10"));
        assertThrows(ClientException.class, () -> registrationRateLimiter.recordRequest("192.0.2.10"));
    }

    @Test
    void shouldRejectWhenSuccessfulRegistrationQuotaIsFull() {
        doReturn(0L).when(stringRedisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThrows(
                ClientException.class,
                () -> registrationRateLimiter.reserveSuccessfulRegistration("192.0.2.10")
        );
    }

    @Test
    void shouldReleaseReservedSuccessfulRegistration() {
        registrationRateLimiter.releaseSuccessfulRegistration("192.0.2.10");

        verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
}
