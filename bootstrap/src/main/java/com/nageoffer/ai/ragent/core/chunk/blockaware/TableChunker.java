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

package com.nageoffer.ai.ragent.core.chunk.blockaware;

import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.core.parser.model.TableBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 表格 chunker：按 {@code maxChars} 体量预算累加切分数据行，<b>每个 chunk 都包含完整表头</b>
 * <p>
 * 关键设计：
 * <ul>
 *   <li>headers 从 TableBlock.headers 直接取，不靠正则提取（vs 老路径的字符串 chunker）</li>
 *   <li>切分按 key-value 渲染长度累加到 maxChars 预算，{@code rowsPerChunk} 仅作硬上限，
 *       兼顾宽表不超 embedding 上限、窄表不过度碎片化；单行体量超预算时保持整行原子，自成一块</li>
 *   <li>content 渲染为完整 markdown 表格（展示）；embeddingText 用 key-value（嵌入）</li>
 *   <li>sectionContext 写入 sheet 名 + 表头摘要，便于检索时回填上下文</li>
 *   <li>无数据行的 TableBlock（仅 headers）：产生一个仅含表头的 chunk</li>
 * </ul>
 */
@Component
public class TableChunker implements BlockChunker<TableBlock> {

    @Override
    public List<VectorChunk> chunk(TableBlock block, ChunkContext ctx) {
        if (block == null) {
            return List.of();
        }
        List<String> headers = block.headers() == null ? List.of() : block.headers();
        List<List<String>> rows = block.rows() == null ? List.of() : block.rows();

        if (headers.isEmpty() && rows.isEmpty()) {
            return List.of();
        }

        // maxChars 为体量预算（按 key-value 渲染长度累加），rowsPerChunk 退化为硬上限
        int budget = Math.max(1, ctx.config().maxChars());
        int maxRows = Math.max(1, ctx.config().rowsPerChunk());
        String sectionContext = buildSectionContext(block);
        List<VectorChunk> result = new ArrayList<>();
        int chunkIndex = ctx.startIndex();

        if (rows.isEmpty()) {
            // 仅表头：产生一个 chunk，标 blockType=TABLE
            result.add(buildChunk(headers, List.of(), block, ctx, chunkIndex, sectionContext));
            return result;
        }

        // 贪心累加：超上限或（非空且加入下一行会超预算）则先切块；单行体量超预算时保持整行原子，自成一块
        List<List<String>> group = new ArrayList<>();
        int groupCost = 0;
        for (List<String> row : rows) {
            int rowCost = renderKeyValueRow(headers, row).length();
            boolean overCap = group.size() >= maxRows;
            boolean overBudget = !group.isEmpty() && groupCost + rowCost > budget;
            if (overCap || overBudget) {
                result.add(buildChunk(headers, group, block, ctx, chunkIndex++, sectionContext));
                group = new ArrayList<>();
                groupCost = 0;
            }
            group.add(row);
            groupCost += rowCost;
        }
        result.add(buildChunk(headers, group, block, ctx, chunkIndex, sectionContext));
        return result;
    }

    private VectorChunk buildChunk(List<String> headers,
                                   List<List<String>> rows,
                                   TableBlock block,
                                   ChunkContext ctx,
                                   int chunkIndex,
                                   String sectionContext) {
        String markdown = renderMarkdownTable(headers, rows);
        String embeddingText = buildEmbeddingText(headers, rows, sectionContext);
        return VectorChunk.builder()
                .chunkId(IdUtil.getSnowflakeNextIdStr())
                .index(chunkIndex)
                .content(markdown)
                .embeddingText(embeddingText)
                .blockType("TABLE")
                .outlinePath(new ArrayList<>(ctx.outlinePath()))
                .sourceBlockIds(List.of(block.id()))
                .sectionContext(sectionContext)
                .build();
    }

    /**
     * 构造嵌入专用文本：sectionContext 作首行 + 每行 key-value
     * <p>
     * markdown 表格的列名↔值靠位置对齐，embedding 模型读不懂位置；改用 {@code 列名: 值}
     * 把语义关系写进字面，sparse/dense 检索均更优（参考 RAGFlow、STC）
     * sectionContext（sheet/表头等）随每块嵌入即 contextual chunking，切碎的行也带表身份
     */
    private String buildEmbeddingText(List<String> headers, List<List<String>> rows, String sectionContext) {
        String kvRows = renderKeyValueRows(headers, rows);
        if (sectionContext == null || sectionContext.isEmpty()) {
            return kvRows;
        }
        if (kvRows.isEmpty()) {
            return sectionContext;
        }
        return sectionContext + "\n" + kvRows;
    }

    /**
     * 把数据行渲染成 key-value 文本：每行用 {@link #renderKeyValueRow} 渲染（跳过整行空），多行用换行连接
     */
    private String renderKeyValueRows(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            String line = renderKeyValueRow(headers, row);
            if (line.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * 单行渲染成 key-value：{@code 列名: 值} 用 "; " 拼接，跳过空值 cell；整行空返回 ""
     * <p>
     * 同时用作 P2 预算切分的行体量度量（length 即该行嵌入文本长度）
     */
    private String renderKeyValueRow(List<String> headers, List<String> row) {
        StringBuilder line = new StringBuilder();
        for (int c = 0; c < row.size(); c++) {
            String value = row.get(c);
            if (value == null || value.isEmpty()) {
                continue;
            }
            String key = c < headers.size() ? headers.get(c) : "";
            if (!line.isEmpty()) {
                line.append("; ");
            }
            if (!key.isEmpty()) {
                line.append(oneLine(key)).append(": ");
            }
            line.append(oneLine(value));
        }
        return line.toString();
    }

    /**
     * 把 cell 内换行压成空格：嵌入文本无需保留换行，避免 key/value 中间夹断行影响检索
     */
    private static String oneLine(String text) {
        return text.replaceAll("\\r\\n|\\r|\\n", " ");
    }

    /**
     * 渲染标准 markdown 表格（| col1 | col2 | + 分隔行 + 数据行）
     */
    private String renderMarkdownTable(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, headers);
        appendSeparator(sb, headers.size());
        for (List<String> row : rows) {
            appendRow(sb, row);
        }
        // 去掉末尾换行
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    private void appendRow(StringBuilder sb, List<String> cells) {
        sb.append('|');
        for (String cell : cells) {
            sb.append(' ').append(sanitizeCell(cell)).append(" |");
        }
        sb.append('\n');
    }

    /**
     * 清洗 cell 以适配 markdown 表格语法
     * <p>
     * 单元格内换行（Excel Alt+Enter）转 {@code <br>}：裸 \n 会从中间截断表格行，使整块退化为普通段落；
     * 竖线转义为 {@code \|}：cell 内的字面 |（如多行表头展平拼接的「财务|收入」）会被误判为列分隔
     */
    private String sanitizeCell(String cell) {
        if (cell == null || cell.isEmpty()) {
            return "";
        }
        return cell.replace("|", "\\|")
                .replaceAll("\\r\\n|\\r|\\n", "<br>");
    }

    private void appendSeparator(StringBuilder sb, int colCount) {
        sb.append('|');
        sb.append("---|".repeat(Math.max(0, colCount)));
        sb.append('\n');
    }

    /**
     * 构造 sectionContext：sheet=<name>; headers=<col1>|<col2>|...
     * <p>
     * 检索 PostProcess 时可拼接到 LLM 上下文前，让 LLM 看到切碎的行 chunk 也有完整表头
     */
    private String buildSectionContext(TableBlock block) {
        StringBuilder ctx = new StringBuilder();
        if (block.provenance() != null && block.provenance().sheetName() != null) {
            ctx.append("sheet=").append(block.provenance().sheetName());
        }
        if (block.captionText() != null && !block.captionText().isEmpty()) {
            if (!ctx.isEmpty()) {
                ctx.append("; ");
            }
            ctx.append("caption=").append(block.captionText());
        }
        if (block.headers() != null && !block.headers().isEmpty()) {
            if (!ctx.isEmpty()) {
                ctx.append("; ");
            }
            // 用 ", " 连接 headers,避免与多行表头内部分隔符 "|" 视觉冲突
            ctx.append("headers=").append(String.join(", ", block.headers()));
        }
        return ctx.isEmpty() ? null : ctx.toString();
    }
}
