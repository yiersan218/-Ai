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

package com.nageoffer.ai.ragent.user.service.impl;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.controller.request.RegisterRequest;
import com.nageoffer.ai.ragent.user.controller.vo.LoginVO;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.user.service.LoginSessionService;
import com.nageoffer.ai.ragent.user.service.PasswordService;
import com.nageoffer.ai.ragent.user.service.RegistrationRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordService passwordService;

    @Mock
    private RegistrationRateLimiter registrationRateLimiter;

    @Mock
    private LoginSessionService loginSessionService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper, passwordService, registrationRateLimiter, loginSessionService);
    }

    @Test
    void shouldCreateUserAndLoginAfterRegistration() {
        RegisterRequest request = registerRequest();
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordService.encode("secure123")).thenReturn("$2a$encoded");
        doAnswer(invocation -> {
            UserDO user = invocation.getArgument(0);
            user.setId("user-1");
            return 1;
        }).when(userMapper).insert(any(UserDO.class));
        when(loginSessionService.create(any(UserDO.class)))
                .thenReturn(new LoginVO("user-1", "user", "token-1", "avatar"));

        LoginVO result = authService.register(request, "192.0.2.10");

        assertEquals("user-1", result.getUserId());
        assertEquals("user", result.getRole());
        assertEquals("token-1", result.getToken());

        verify(registrationRateLimiter).reserveSuccessfulRegistration("192.0.2.10");
        verify(registrationRateLimiter, never()).releaseSuccessfulRegistration(any());
        verify(loginSessionService).create(any(UserDO.class));
    }

    @Test
    void shouldReleaseSuccessfulQuotaWhenInsertFails() {
        RegisterRequest request = registerRequest();
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordService.encode("secure123")).thenReturn("$2a$encoded");
        doThrow(new DuplicateKeyException("duplicate")).when(userMapper).insert(any(UserDO.class));

        assertThrows(ClientException.class, () -> authService.register(request, "192.0.2.10"));

        verify(registrationRateLimiter).reserveSuccessfulRegistration("192.0.2.10");
        verify(registrationRateLimiter).releaseSuccessfulRegistration("192.0.2.10");
    }

    @Test
    void shouldRejectMismatchedPasswordsBeforeReservingQuota() {
        RegisterRequest request = registerRequest();
        request.setConfirmPassword("different123");

        assertThrows(ClientException.class, () -> authService.register(request, "192.0.2.10"));

        verify(registrationRateLimiter, never()).reserveSuccessfulRegistration(any());
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new-user");
        request.setPassword("secure123");
        request.setConfirmPassword("secure123");
        return request;
    }
}
