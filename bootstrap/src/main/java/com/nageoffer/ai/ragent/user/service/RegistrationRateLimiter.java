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

import cn.hutool.crypto.digest.DigestUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.user.config.RegistrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistrationRateLimiter {

    private static final String REQUEST_KEY_PREFIX = "ragent:auth:register:request:";
    private static final String SUCCESS_KEY_PREFIX = "ragent:auth:register:success:";

    private static final RedisScript<Long> INCREMENT_WITH_EXPIRY_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private static final RedisScript<Long> RESERVE_SUCCESS_SCRIPT = RedisScript.of("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[2])
            if current >= limit then
                return 0
            end
            current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private static final RedisScript<Long> RELEASE_SUCCESS_SCRIPT = RedisScript.of("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current <= 1 then
                redis.call('DEL', KEYS[1])
                return 0
            end
            return redis.call('DECR', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final RegistrationProperties registrationProperties;

    public void recordRequest(String clientIp) {
        Long count = stringRedisTemplate.execute(
                INCREMENT_WITH_EXPIRY_SCRIPT,
                List.of(buildKey(REQUEST_KEY_PREFIX, clientIp)),
                String.valueOf(registrationProperties.getWindowSeconds())
        );
        if (count == null) {
            throw new ServiceException("注册限流服务暂时不可用");
        }
        if (count > registrationProperties.getRequestLimit()) {
            throw new ClientException("注册请求过于频繁，请 10 分钟后再试");
        }
    }

    public void reserveSuccessfulRegistration(String clientIp) {
        Long count = stringRedisTemplate.execute(
                RESERVE_SUCCESS_SCRIPT,
                List.of(buildKey(SUCCESS_KEY_PREFIX, clientIp)),
                String.valueOf(registrationProperties.getWindowSeconds()),
                String.valueOf(registrationProperties.getSuccessLimit())
        );
        if (count == null) {
            throw new ServiceException("注册限流服务暂时不可用");
        }
        if (count == 0L) {
            throw new ClientException("该网络短时间内注册账号过多，请 10 分钟后再试");
        }
    }

    public void releaseSuccessfulRegistration(String clientIp) {
        stringRedisTemplate.execute(
                RELEASE_SUCCESS_SCRIPT,
                List.of(buildKey(SUCCESS_KEY_PREFIX, clientIp))
        );
    }

    private String buildKey(String prefix, String clientIp) {
        return prefix + DigestUtil.sha256Hex(clientIp);
    }
}
