-- v1.1.0 260722-02 会话消息推荐追问上下文字段升级
-- 会话消息新增推荐追问缓存、grounding 片段、问答关联与结束状态

ALTER TABLE t_message ADD COLUMN IF NOT EXISTS recommended_questions JSONB;
ALTER TABLE t_message ADD COLUMN IF NOT EXISTS retrieved_chunks JSONB;
ALTER TABLE t_message ADD COLUMN IF NOT EXISTS reply_to_message_id VARCHAR(20);
ALTER TABLE t_message ADD COLUMN IF NOT EXISTS message_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL';

COMMENT ON COLUMN t_message.recommended_questions IS '推荐追问问题';
COMMENT ON COLUMN t_message.retrieved_chunks IS '推荐问题 grounding 片段';
COMMENT ON COLUMN t_message.reply_to_message_id IS '当前助手消息对应的用户消息ID';
COMMENT ON COLUMN t_message.message_status IS '消息结束状态：NORMAL=正常完成，INTERRUPTED=用户中断，REJECTED=限流拒绝';
