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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.controller.request.LoginRequest;
import com.nageoffer.ai.ragent.user.controller.request.RegisterRequest;
import com.nageoffer.ai.ragent.user.controller.vo.LoginVO;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.user.enums.UserRole;
import com.nageoffer.ai.ragent.user.service.AuthService;
import com.nageoffer.ai.ragent.user.service.LoginSessionService;
import com.nageoffer.ai.ragent.user.service.PasswordService;
import com.nageoffer.ai.ragent.user.service.RegistrationRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final RegistrationRateLimiter registrationRateLimiter;
    private final LoginSessionService loginSessionService;

    @Override
    public LoginVO login(LoginRequest requestParam) {
        String username = StrUtil.trimToNull(requestParam.getUsername());
        String password = requestParam.getPassword();
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            throw new ClientException("用户名或密码不能为空");
        }
        UserDO user = findByUsername(username);
        if (user == null || !passwordService.matches(password, user.getPassword())) {
            throw new ClientException("用户名或密码错误");
        }
        if (!passwordService.isEncoded(user.getPassword())) {
            user.setPassword(passwordService.encode(password));
            userMapper.updateById(user);
        }
        return loginSessionService.create(user);
    }

    @Override
    public LoginVO register(RegisterRequest requestParam, String clientIp) {
        if (requestParam == null) {
            throw new ClientException("注册信息不能为空");
        }
        String username = StrUtil.trimToNull(requestParam.getUsername());
        String password = requestParam.getPassword();
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            throw new ClientException("用户名或密码不能为空");
        }
        if (!password.equals(requestParam.getConfirmPassword())) {
            throw new ClientException("两次输入的密码不一致");
        }
        if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
            throw new ClientException("该用户名不可用");
        }
        if (findByUsername(username) != null) {
            throw new ClientException("用户名已存在");
        }

        String encodedPassword = passwordService.encode(password);
        registrationRateLimiter.reserveSuccessfulRegistration(clientIp);
        boolean created = false;
        UserDO user = UserDO.builder()
                .username(username)
                .password(encodedPassword)
                .role(UserRole.USER.getCode())
                .build();
        try {
            userMapper.insert(user);
            created = true;
        } catch (DuplicateKeyException ex) {
            throw new ClientException("用户名已存在");
        } finally {
            if (!created) {
                registrationRateLimiter.releaseSuccessfulRegistration(clientIp);
            }
        }
        return loginSessionService.create(user);
    }

    @Override
    public void logout() {
        loginSessionService.logout();
    }

    private UserDO findByUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return null;
        }
        return userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username)
                        .eq(UserDO::getDeleted, 0)
        );
    }

}
