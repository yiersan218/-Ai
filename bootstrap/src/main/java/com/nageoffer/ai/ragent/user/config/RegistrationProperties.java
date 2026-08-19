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

package com.nageoffer.ai.ragent.user.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.registration")
public class RegistrationProperties {

    /**
     * 注册总开关。关闭后保留实现代码，但拒绝所有注册请求。
     */
    private boolean enabled = true;

    /**
     * 仅当应用只接受来自可信反向代理的流量，且代理会覆盖客户端传入的转发头时开启。
     */
    private boolean trustForwardedHeaders;

    @Min(1)
    private int windowSeconds = 600;

    @Min(1)
    private int requestLimit = 20;

    @Min(1)
    private int successLimit = 3;
}
