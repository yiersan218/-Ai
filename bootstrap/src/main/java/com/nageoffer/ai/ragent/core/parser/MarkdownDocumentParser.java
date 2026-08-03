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

package com.nageoffer.ai.ragent.core.parser;

import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.CodeBlock;
import com.nageoffer.ai.ragent.core.parser.model.HeadingBlock;
import com.nageoffer.ai.ragent.core.parser.model.ListBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParagraphBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Markdown 文档解析器
 * <p>
 * v1.1 升级（M6 / P1.9）：用 commonmark-java 解析 AST,输出真正结构化的 Block 列表
 * <ul>
 *   <li>{@code # 标题} → {@link com.nageoffer.ai.ragent.core.parser.model.HeadingBlock}</li>
 *   <li>普通段落 → {@link ParagraphBlock}</li>
 *   <li>{@code ```...```} → {@link CodeBlock}</li>
 *   <li>{@code - / 1.} 列表 → {@link ListBlock}</li>
 *   <li>GFM 表格 → 自定义 TableBlock</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MarkdownDocumentParser implements DocumentParser {

    /**
     * 解析器（线程安全,共享）
     */
    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    @Override
    public String getParserType() {
        return ParserType.MARKDOWN.getType();
    }

    @Override
    public ParsedDocument parseStructured(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            return ParsedDocument.of(List.of());
        }

        String text = new String(content, StandardCharsets.UTF_8);
        Provenance prov = Provenance.ofFile(extractSourceFile(options));

        Document doc = (Document) PARSER.parse(text);
        BlockExtractingVisitor visitor = new BlockExtractingVisitor(prov);
        doc.accept(visitor);

        return ParsedDocument.of(visitor.getBlocks(), Map.of(
                "parser", getParserType(),
                "mimeType", mimeType == null ? "" : mimeType,
                "blocks", visitor.getBlocks().size()
        ));
    }

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && (
                mimeType.equals("text/markdown") ||
                        mimeType.equals("text/x-markdown") ||
                        mimeType.equals("text/plain")
        );
    }

    private static String extractSourceFile(Map<String, Object> options) {
        if (options == null) {
            return "";
        }
        Object v = options.get("sourceFile");
        return v == null ? "" : v.toString();
    }

    // ===================== AST Visitor =====================

    /**
     * AST 访问器:把 commonmark 节点转换为 ragent Block 列表
     * 只处理顶层 block 元素;不递归进入嵌套(如列表项内的代码块仍属于 ListBlock)
     */
    private static final class BlockExtractingVisitor extends AbstractVisitor {

        private final Provenance provenance;
        private final List<Block> blocks = new ArrayList<>();

        BlockExtractingVisitor(Provenance provenance) {
            this.provenance = provenance;
        }

        List<Block> getBlocks() {
            return blocks;
        }

        @Override
        public void visit(Heading heading) {
            blocks.add(new HeadingBlock(
                    UUID.randomUUID().toString(),
                    provenance,
                    Collections.emptyList(),
                    heading.getLevel(),
                    extractInlineText(heading)
            ));
            // 不向下递归(标题内的 inline 已合并)
        }

        @Override
        public void visit(Paragraph paragraph) {
            // 段落可能是顶层段落,也可能是列表项内的;只处理顶层
            if (paragraph.getParent() instanceof ListItem) {
                return;
            }
            String text = extractInlineText(paragraph);
            if (!text.isEmpty()) {
                blocks.add(new ParagraphBlock(
                        UUID.randomUUID().toString(),
                        provenance,
                        Collections.emptyList(),
                        text
                ));
            }
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            blocks.add(new CodeBlock(
                    UUID.randomUUID().toString(),
                    provenance,
                    Collections.emptyList(),
                    codeBlock.getInfo(),
                    stripTrailingNewline(codeBlock.getLiteral())
            ));
        }

        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            blocks.add(new CodeBlock(
                    UUID.randomUUID().toString(),
                    provenance,
                    Collections.emptyList(),
                    null,
                    stripTrailingNewline(codeBlock.getLiteral())
            ));
        }

        @Override
        public void visit(BulletList bulletList) {
            blocks.add(buildListBlock(bulletList, false));
            // 不向下递归
        }

        @Override
        public void visit(OrderedList orderedList) {
            blocks.add(buildListBlock(orderedList, true));
            // 不向下递归
        }

        @Override
        public void visit(org.commonmark.node.CustomBlock customBlock) {
            // GFM TableBlock 是 CustomBlock 子类
            if (customBlock instanceof TableBlock tableBlock) {
                handleTable(tableBlock);
                return;
            }
            super.visit(customBlock);
        }

        private ListBlock buildListBlock(Node listNode, boolean ordered) {
            List<String> items = new ArrayList<>();
            Node child = listNode.getFirstChild();
            while (child != null) {
                if (child instanceof ListItem) {
                    items.add(extractInlineText(child).trim());
                }
                child = child.getNext();
            }
            return new ListBlock(
                    UUID.randomUUID().toString(),
                    provenance,
                    Collections.emptyList(),
                    ordered,
                    items
            );
        }

        private void handleTable(TableBlock tableBlock) {
            List<String> headers = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();

            Node child = tableBlock.getFirstChild();
            while (child != null) {
                if (child instanceof TableHead head) {
                    Node hr = head.getFirstChild();
                    if (hr instanceof TableRow tr) {
                        headers.addAll(extractCellTexts(tr));
                    }
                } else if (child instanceof TableBody body) {
                    Node tr = body.getFirstChild();
                    while (tr != null) {
                        if (tr instanceof TableRow row) {
                            rows.add(extractCellTexts(row));
                        }
                        tr = tr.getNext();
                    }
                }
                child = child.getNext();
            }

            blocks.add(new com.nageoffer.ai.ragent.core.parser.model.TableBlock(
                    UUID.randomUUID().toString(),
                    provenance,
                    Collections.emptyList(),
                    headers,
                    rows,
                    null
            ));
        }

        private List<String> extractCellTexts(TableRow row) {
            List<String> cells = new ArrayList<>();
            Node cell = row.getFirstChild();
            while (cell != null) {
                if (cell instanceof TableCell tc) {
                    cells.add(extractInlineText(tc).trim());
                }
                cell = cell.getNext();
            }
            return cells;
        }
    }

    /**
     * 提取节点内所有 inline 文本(连接 Text / Code / Link / Emphasis 等)，
     * 保留 Link 为 {@code [text](url)} 形式以便下游 ImageChunker 风格一致
     */
    private static String extractInlineText(Node parent) {
        StringBuilder sb = new StringBuilder();
        Node child = parent.getFirstChild();
        while (child != null) {
            appendInline(sb, child);
            child = child.getNext();
        }
        return sb.toString();
    }

    private static void appendInline(StringBuilder sb, Node node) {
        if (node instanceof Text t) {
            sb.append(t.getLiteral());
        } else if (node instanceof Code code) {
            sb.append('`').append(code.getLiteral()).append('`');
        } else if (node instanceof Link link) {
            String inner = extractInlineText(link);
            String dest = link.getDestination();
            sb.append('[').append(inner).append("](").append(dest).append(')');
        } else if (node instanceof Emphasis || node instanceof StrongEmphasis) {
            // 保留 inline 文本但不保留 markdown 标记（简化）
            sb.append(extractInlineText(node));
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            sb.append('\n');
        } else if (node.getFirstChild() != null) {
            Node child = node.getFirstChild();
            while (child != null) {
                appendInline(sb, child);
                child = child.getNext();
            }
        }
    }

    private static String stripTrailingNewline(String s) {
        if (s == null) {
            return "";
        }
        if (s.endsWith("\n")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }
}
