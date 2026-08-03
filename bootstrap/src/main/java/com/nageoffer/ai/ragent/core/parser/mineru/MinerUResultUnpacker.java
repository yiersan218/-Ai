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

package com.nageoffer.ai.ragent.core.parser.mineru;

import com.nageoffer.ai.ragent.core.parser.model.AssetRef;
import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.CodeBlock;
import com.nageoffer.ai.ragent.core.parser.model.HeadingBlock;
import com.nageoffer.ai.ragent.core.parser.model.ImageBlock;
import com.nageoffer.ai.ragent.core.parser.model.ListBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParagraphBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Image;
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
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU 结果解包器:zip 字节流 → ParsedDocument
 * <p>
 * 流程:
 * <ol>
 *   <li>解 zip 收集所有 entry(markdown + 图片)</li>
 *   <li>对每个图片字节:上传到 RustFS asset-bucket 拿到 URL</li>
 *   <li>记录 {zip 内路径 → RustFS URL} 映射</li>
 *   <li>commonmark 解析 markdown AST,遍历:
 *     <ul>
 *       <li>"段首图片" → 提升为 {@link ImageBlock},asset 关联 RustFS URL</li>
 *       <li>其他 block → 转 ragent Block</li>
 *     </ul>
 *   </li>
 * </ol>
 */
@Slf4j
@Component
public class MinerUResultUnpacker {

    private static final Parser MARKDOWN_PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    private final FileStorageService fileStorageService;

    public MinerUResultUnpacker(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * 解包 MinerU zip 输出为 ParsedDocument
     *
     * @param zipBytes   MinerU 返回的 zip 字节流
     * @param sourceFile 文档来源标识,写入 Provenance.sourceFile
     * @param documentId 文档 ID,用于资产 key 命名 {@code assets/{documentId}/{uuid}.{ext}}
     * @return 含 Block 列表的 ParsedDocument,ImageBlock 已携带 RustFS AssetRef
     */
    public ParsedDocument unpack(byte[] zipBytes, String sourceFile, String documentId) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new ServiceException("MinerU zip 字节为空");
        }

        ZipContents contents = readZip(zipBytes);
        if (contents.markdown == null) {
            throw new ServiceException("MinerU zip 中未找到 markdown 文件");
        }

        // 上传所有图片到 RustFS,得 {zipPath → rustfsUrl} 映射
        Map<String, String> imageUrlMap = uploadImages(contents.images, documentId);

        // 解析 markdown 输出 Block 列表
        Provenance prov = Provenance.ofFile(sourceFile);
        Document doc = (Document) MARKDOWN_PARSER.parse(contents.markdown);
        UnpackVisitor visitor = new UnpackVisitor(prov, imageUrlMap);
        doc.accept(visitor);

        return ParsedDocument.of(visitor.getBlocks(), Map.of(
                "parser", "MinerU",
                "imagesUploaded", imageUrlMap.size(),
                "blocks", visitor.getBlocks().size()
        ));
    }

    /**
     * 单文件 zip 内容快照
     */
    private record ZipContents(String markdown, Map<String, byte[]> images) {
    }

    private ZipContents readZip(byte[] zipBytes) {
        String markdown = null;
        Map<String, byte[]> images = new HashMap<>();

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                byte[] data = readAll(zin);

                if (name.toLowerCase(Locale.ROOT).endsWith(".md") && markdown == null) {
                    markdown = new String(data, StandardCharsets.UTF_8);
                } else if (isImage(name)) {
                    images.put(name, data);
                }
            }
        } catch (IOException e) {
            throw new ServiceException("MinerU zip 解压失败: " + e.getMessage());
        }
        return new ZipContents(markdown, images);
    }

    private static byte[] readAll(ZipInputStream zin) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zin.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    /**
     * 上传所有图片到 RustFS，返回 {zipPath → 公开访问 URL}
     */
    private Map<String, String> uploadImages(Map<String, byte[]> images, String documentId) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, byte[]> e : images.entrySet()) {
            String zipPath = e.getKey();
            byte[] data = e.getValue();
            String ext = extractExt(zipPath);
            String filename = "assets/" + documentId + "/" + UUID.randomUUID() + "." + ext;
            String mime = inferMime(ext);
            try {
                StoredFileDTO stored = fileStorageService.uploadAsset(data, filename, mime);
                // 转为浏览器可直连的公开 URL(资产桶已开公共读)，供 markdown 图片链接固化入库
                String publicUrl = fileStorageService.getPublicUrl(stored.getUrl());
                result.put(zipPath, publicUrl);
            } catch (Exception ex) {
                log.error("MinerU 图片上传失败 zipPath={}", zipPath, ex);
                throw new ServiceException("MinerU 图片上传失败 " + zipPath + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private static String extractExt(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1).toLowerCase(Locale.ROOT) : "bin";
    }

    private static String inferMime(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    // ===================== AST Visitor =====================

    /**
     * 遍历 markdown AST 输出 Block
     * <p>
     * 与 MarkdownDocumentParser 的 Visitor 类似,但额外:
     * <ul>
     *   <li>剥离"段首 Image",提升为 {@link ImageBlock} + RustFS AssetRef(剩余内容另起 ParagraphBlock)</li>
     *   <li>行内 Image 保留在 ParagraphBlock 中，链接已替换为 RustFS URL</li>
     * </ul>
     */
    private static final class UnpackVisitor extends AbstractVisitor {

        private final Provenance provenance;
        private final Map<String, String> imageUrlMap;
        private final List<Block> blocks = new ArrayList<>();

        UnpackVisitor(Provenance provenance, Map<String, String> imageUrlMap) {
            this.provenance = provenance;
            this.imageUrlMap = imageUrlMap;
        }

        List<Block> getBlocks() {
            return blocks;
        }

        @Override
        public void visit(Heading heading) {
            blocks.add(new HeadingBlock(
                    UUID.randomUUID().toString(),
                    provenance,
                    List.of(),
                    heading.getLevel(),
                    extractInlineText(heading)
            ));
        }

        @Override
        public void visit(Paragraph paragraph) {
            if (paragraph.getParent() instanceof ListItem) {
                return;
            }

            Node rest = paragraph.getFirstChild();
            while (rest != null) {
                if (rest instanceof Image img) {
                    handleStandaloneImage(img);
                } else if (!isBlank(rest)) {
                    break;
                }
                rest = rest.getNext();
            }

            String text = extractInlineTextFrom(rest);
            if (!text.isEmpty()) {
                blocks.add(new ParagraphBlock(
                        UUID.randomUUID().toString(),
                        provenance,
                        List.of(),
                        text
                ));
            }
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            blocks.add(new CodeBlock(
                    UUID.randomUUID().toString(),
                    provenance,
                    List.of(),
                    codeBlock.getInfo(),
                    stripTrailingNewline(codeBlock.getLiteral())
            ));
        }

        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            blocks.add(new CodeBlock(
                    UUID.randomUUID().toString(),
                    provenance,
                    List.of(),
                    null,
                    stripTrailingNewline(codeBlock.getLiteral())
            ));
        }

        @Override
        public void visit(BulletList bulletList) {
            blocks.add(buildListBlock(bulletList, false));
        }

        @Override
        public void visit(OrderedList orderedList) {
            blocks.add(buildListBlock(orderedList, true));
        }

        @Override
        public void visit(CustomBlock customBlock) {
            if (customBlock instanceof TableBlock tableBlock) {
                handleTable(tableBlock);
                return;
            }
            super.visit(customBlock);
        }

        /**
         * 处理 HTML 块:MinerU 的表格等以原始 HTML(如 {@code <table>})嵌在 markdown 里，
         * commonmark 解析为 HtmlBlock,这里原样保留 HTML 文本写入，避免内容被丢弃
         * (底层已兼容 HTML，无需转 Markdown 语法)
         */
        @Override
        public void visit(HtmlBlock htmlBlock) {
            String html = htmlBlock.getLiteral() == null ? "" : htmlBlock.getLiteral().strip();
            if (html.isEmpty()) {
                return;
            }
            blocks.add(new ParagraphBlock(
                    UUID.randomUUID().toString(),
                    provenance,
                    List.of(),
                    html
            ));
        }

        /**
         * 判断节点是否为可跳过的空白(换行或纯空白文本)
         */
        private static boolean isBlank(Node node) {
            return node instanceof SoftLineBreak
                    || node instanceof HardLineBreak
                    || (node instanceof Text t && t.getLiteral().trim().isEmpty());
        }

        private void handleStandaloneImage(Image image) {
            String rawDest = image.getDestination();
            String resolved = resolveImageUrl(rawDest);
            String caption = extractInlineText(image);
            String blockId = UUID.randomUUID().toString();

            AssetRef asset = new AssetRef(
                    resolved,
                    inferMimeFromUrl(resolved),
                    blockId
            );
            blocks.add(new ImageBlock(
                    blockId, provenance, List.of(),
                    asset, caption, caption
            ));
        }

        private String resolveImageUrl(String rawDest) {
            if (rawDest == null) {
                return "";
            }
            // 优先精确匹配
            String url = imageUrlMap.get(rawDest);
            if (url != null) {
                return url;
            }
            // 尝试模糊匹配(MinerU markdown 里可能用 ./images/xxx 或 images/xxx)
            String norm = rawDest.replaceFirst("^\\./", "");
            url = imageUrlMap.get(norm);
            if (url != null) {
                return url;
            }
            // 用文件名匹配兜底
            int idx = norm.lastIndexOf('/');
            String fileName = idx >= 0 ? norm.substring(idx + 1) : norm;
            for (Map.Entry<String, String> e : imageUrlMap.entrySet()) {
                if (e.getKey().endsWith("/" + fileName) || e.getKey().equals(fileName)) {
                    return e.getValue();
                }
            }
            return rawDest;
        }

        private static String inferMimeFromUrl(String url) {
            String lower = url.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".webp")) return "image/webp";
            if (lower.endsWith(".gif")) return "image/gif";
            return "application/octet-stream";
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
                    List.of(),
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
                    List.of(),
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

        private String extractInlineText(Node parent) {
            return extractInlineTextFrom(parent.getFirstChild());
        }

        /**
         * 从指定兄弟节点起拼接 inline 文本（供段首剥离图片后渲染剩余内容）
         */
        private String extractInlineTextFrom(Node start) {
            StringBuilder sb = new StringBuilder();
            Node node = start;
            while (node != null) {
                appendInline(sb, node);
                node = node.getNext();
            }
            return sb.toString();
        }

        private void appendInline(StringBuilder sb, Node node) {
            if (node instanceof Text t) {
                sb.append(t.getLiteral());
            } else if (node instanceof Code code) {
                sb.append('`').append(code.getLiteral()).append('`');
            } else if (node instanceof Link link) {
                String inner = extractInlineText(link);
                sb.append('[').append(inner).append("](").append(link.getDestination()).append(')');
            } else if (node instanceof Image img) {
                // inline 图片(非 standalone)保留 [alt](rustfsUrl) 形式
                String alt = extractInlineText(img);
                String resolved = resolveImageUrl(img.getDestination());
                sb.append("![").append(alt).append("](").append(resolved).append(')');
            } else if (node instanceof Emphasis || node instanceof StrongEmphasis) {
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
            return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
        }
    }
}
