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

package com.nageoffer.ai.ragent.core.parser.model;

/**
 * Block 来源信息(溯源用)
 * <p>
 * 用于检索时拼接 sectionContext、排障时定位原始文档位置
 *
 * @param sourceFile 原始文件标识(文件 ID 或文件名)
 * @param sheetName  Excel sheet 名,非 Excel 来源为 null
 */
public record Provenance(String sourceFile, String sheetName) {

    /**
     * 仅含文件来源的最小构造
     */
    public static Provenance ofFile(String sourceFile) {
        return new Provenance(sourceFile, null);
    }

    /**
     * Excel 来源构造
     */
    public static Provenance ofExcelCell(String sourceFile, String sheetName) {
        return new Provenance(sourceFile, sheetName);
    }
}
