-- 将已部署意图树中的旧 MCP 工具 ID 迁移到腾讯云联网搜索实现。
-- 幂等执行；只修改仍指向旧工具 ID 的有效节点。
BEGIN;

UPDATE t_intent_node
SET mcp_tool_id = 'tencent_search',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE mcp_tool_id = 'youcom_search'
  AND deleted = 0;

COMMIT;
