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

package com.nageoffer.ai.ragent.core.parser.excel;

import com.nageoffer.ai.ragent.core.parser.DocumentParser;
import com.nageoffer.ai.ragent.core.parser.ParserType;
import com.nageoffer.ai.ragent.core.parser.excel.ExcelTableNormalizer.NormalizedTable;
import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.core.parser.model.TableBlock;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Excel 文档解析器（Apache POI）
 * <p>
 * <b>M3 完整版</b>：使用 {@link ExcelTableNormalizer} 处理：
 * <ul>
 *   <li>合并单元格展开填充</li>
 *   <li>多行表头展平拼接</li>
 *   <li>超链接 cell 内联为 {@code [text](url)}</li>
 *   <li>公式 cell 求值 + 回退到缓存值 / 公式字符串</li>
 * </ul>
 * <p>
 * <b>解析选项</b>（通过 {@code options} Map 传入）：
 * <ul>
 *   <li>{@code sourceFile}: 文件标识，写入 Provenance.sourceFile</li>
 *   <li>{@code headerRows}: 表头占用的行数，默认 1（用于多行表头展平）</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ExcelDocumentParser implements DocumentParser {

    public static final String OPT_SOURCE_FILE = "sourceFile";
    public static final String OPT_HEADER_ROWS = "headerRows";

    private static final int DEFAULT_HEADER_ROWS = 1;

    @Override
    public String getParserType() {
        return ParserType.EXCEL_POI.getType();
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || mimeType.equals("application/vnd.ms-excel")
                || mimeType.equals("application/x-tika-msoffice")
                || mimeType.equals("application/x-tika-ooxml");
    }

    @Override
    public ParsedDocument parseStructured(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            return ParsedDocument.of(List.of());
        }

        String sourceFile = extractString(options);
        int headerRows = extractInt(options);

        List<Block> blocks = new ArrayList<>();
        int totalSheets;

        try (ByteArrayInputStream is = new ByteArrayInputStream(content);
             Workbook workbook = WorkbookFactory.create(is)) {

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            totalSheets = workbook.getNumberOfSheets();
            for (int i = 0; i < totalSheets; i++) {
                if (workbook.isSheetHidden(i) || workbook.isSheetVeryHidden(i)) {
                    log.info("跳过隐藏 sheet[{}]，不纳入解析结果", workbook.getSheetName(i));
                    continue;
                }
                Sheet sheet = workbook.getSheetAt(i);
                List<TableBlock> sheetBlocks = buildTableBlocks(sheet, sourceFile, headerRows, formatter, evaluator);
                blocks.addAll(sheetBlocks);
            }
        } catch (Exception e) {
            log.error("Excel 解析失败，MIME 类型: {}, 文件大小: {} bytes", mimeType, content.length, e);
            throw new ServiceException("Excel 解析失败: " + e.getMessage());
        }

        return ParsedDocument.of(blocks, Map.of(
                "parser", getParserType(),
                "mimeType", mimeType == null ? "" : mimeType,
                "totalSheets", totalSheets,
                "parsedTables", blocks.size(),
                "headerRows", headerRows
        ));
    }

    /**
     * 用 {@link ExcelTableNormalizer} 规范化 sheet 为单张表，产出 0 或 1 个 TableBlock
     */
    private List<TableBlock> buildTableBlocks(Sheet sheet, String sourceFile, int headerRows,
                                              DataFormatter formatter, FormulaEvaluator evaluator) {
        NormalizedTable table = ExcelTableNormalizer.normalize(sheet, formatter, evaluator, headerRows);
        if (table.isEmpty()) {
            log.debug("Sheet [{}] 为空，跳过", sheet.getSheetName());
            return List.of();
        }

        Provenance prov = Provenance.ofExcelCell(sourceFile, sheet.getSheetName());
        TableBlock block = new TableBlock(
                UUID.randomUUID().toString(),
                prov,
                List.of(),
                table.headers(),
                table.rows(),
                null
        );
        return List.of(block);
    }

    private static String extractString(Map<String, Object> options) {
        if (options == null) {
            return "";
        }
        Object v = options.get(ExcelDocumentParser.OPT_SOURCE_FILE);
        return v == null ? "" : v.toString();
    }

    private static int extractInt(Map<String, Object> options) {
        if (options == null) {
            return ExcelDocumentParser.DEFAULT_HEADER_ROWS;
        }
        Object v = options.get(ExcelDocumentParser.OPT_HEADER_ROWS);
        if (v == null) {
            return ExcelDocumentParser.DEFAULT_HEADER_ROWS;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return ExcelDocumentParser.DEFAULT_HEADER_ROWS;
        }
    }
}
