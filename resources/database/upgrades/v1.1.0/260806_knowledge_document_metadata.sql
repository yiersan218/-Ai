-- Markdown frontmatter 文档级元数据，用于检索过滤与来源追溯。
ALTER TABLE t_knowledge_document
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN t_knowledge_document.metadata IS '解析器提取的文档级元数据（如 Markdown frontmatter）';
