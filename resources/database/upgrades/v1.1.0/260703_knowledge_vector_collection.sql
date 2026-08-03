-- v1.1.0 260703 知识向量 Collection 字段升级
-- t_knowledge_vector 表：新增知识库 Collection 字段

ALTER TABLE t_knowledge_vector
    ADD COLUMN collection_name VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX idx_kv_collection_name
    ON t_knowledge_vector (collection_name);

COMMENT ON COLUMN t_knowledge_vector.collection_name IS '知识库Collection';
