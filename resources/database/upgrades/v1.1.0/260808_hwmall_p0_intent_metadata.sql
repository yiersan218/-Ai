-- 华为商城 P0 意图元数据同步：保持现有树结构，仅扩展商品快照、退换货退款和发票语义。
-- 执行后需重启应用或清理 Redis 中的意图树缓存，确保运行时重新读取数据库。
BEGIN;

UPDATE t_intent_node
SET description = '13 类华为硬件的产品家族、稳定规格、功能和核心卖点，以及带采集时间的官网 SKU 配置、颜色和页面标价快照；不提供实时库存、最终结算价或个人优惠。',
    examples = '["HUAWEI WATCH 5 有哪些卖点","Pura 90 Pro 的影像参数是什么","Pura 90 Pro 有哪些颜色和配置","Pura 90 Pro 现在页面标价多少"]',
    prompt_snippet = '优先使用知识库明确资料回答；官网配置、颜色和页面标价快照仅在同一商品/SKU且采集时间、地区和有效期明确时转述；实时库存、最终结算价和当前优惠证据不足时再回退 MCP；不得回答个人业务数据。',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE intent_code = 'kb-product-parameters' AND deleted = 0;

UPDATE t_intent_node
SET description = '七天无理由、质量问题、自营与第三方商品退换货、极速退款、申请材料、退货运费、退款方式与周期，以及积分、优惠和发票相关权益返还规则。',
    examples = '["华为商城退货需要什么条件","七天无理由退货拆封后还能退吗","第三方商品退货运费谁承担","极速退款为什么少于实付金额","退款后积分和优惠券会退回吗"]',
    prompt_snippet = '只说明公开政策并区分自营、第三方、质量问题与无理由退货，不承诺个案审核或到账结果。',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE intent_code = 'kb-return-refund' AND deleted = 0;

UPDATE t_intent_node
SET description = '华为商城发票开具与下载、抬头修改、180 天内换开、电子发票法律效力与真伪查验、报销及设备售后凭证规则。',
    examples = '["电子发票如何开具","发票信息填错了如何换开","电子发票能用于报销吗","如何查验华为商城发票真伪","发票丢了还能办理设备保修吗"]',
    prompt_snippet = '只说明公开开票、换开、查验和售后凭证规则，不代替税务、财务或个案审核。',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE intent_code = 'kb-invoice' AND deleted = 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT required.intent_code
        FROM (VALUES
            ('kb-product-parameters'),
            ('kb-return-refund'),
            ('kb-invoice')
        ) AS required(intent_code)
        WHERE NOT EXISTS (
            SELECT 1
            FROM t_intent_node node
            WHERE node.intent_code = required.intent_code AND node.deleted = 0
        )
    ) THEN
        RAISE EXCEPTION 'P0 意图元数据同步失败：存在缺失的华为商城意图节点';
    END IF;
END $$;

COMMIT;
