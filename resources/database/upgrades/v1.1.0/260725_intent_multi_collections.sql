-- v1.1.0 260725 意图多 Collection 绑定升级
-- 一个知识库意图支持关联多个 Collection；旧的单 Collection 配置自动迁移为单元素数组

ALTER TABLE t_intent_node
    ADD COLUMN IF NOT EXISTS collection_names JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE t_intent_node
SET collection_names = jsonb_build_array(collection_name)
WHERE collection_name IS NOT NULL
  AND btrim(collection_name) <> ''
  AND collection_names = '[]'::jsonb;

COMMENT ON COLUMN t_intent_node.collection_name IS '兼容旧版本，后续删除';
COMMENT ON COLUMN t_intent_node.collection_names IS '知识库Collection集合';
