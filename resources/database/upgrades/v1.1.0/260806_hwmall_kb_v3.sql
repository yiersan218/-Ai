-- 华为商城 KBpro v3：7 个知识库 + 21 节点意图树。
-- 执行前请备份数据库；执行后重启服务或清理意图树缓存。
BEGIN;

-- 现有 hwmallwarranty 保留并收敛为“保修与维修”。
UPDATE t_knowledge_base
SET name = '保修与维修 KB', updated_by = 'system', update_time = CURRENT_TIMESTAMP
WHERE collection_name = 'hwmallwarranty' AND deleted = 0;

-- 空的旧“发票与售后”容器复用为独立发票库；若已存在新库则只更新新库名称。
UPDATE t_knowledge_base
SET name = '发票 KB', collection_name = 'hwmallinvoice', updated_by = 'system', update_time = CURRENT_TIMESTAMP
WHERE collection_name = 'hwmallaftersales'
  AND deleted = 0
  AND NOT EXISTS (SELECT 1 FROM t_knowledge_base WHERE collection_name = 'hwmallinvoice' AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM t_knowledge_document d WHERE d.kb_id = t_knowledge_base.id AND d.deleted = 0);

UPDATE t_knowledge_base
SET name = '发票 KB', updated_by = 'system', update_time = CURRENT_TIMESTAMP
WHERE collection_name = 'hwmallinvoice' AND deleted = 0;

-- 新增退货换货与退款库，嵌入模型沿用现有华为商城知识库。
INSERT INTO t_knowledge_base
    (id, name, embedding_model, collection_name, created_by, updated_by, create_time, update_time, deleted)
SELECT '2608060000000000001', '退货换货与退款 KB', source.embedding_model,
       'hwmallreturn', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM (
    SELECT embedding_model
    FROM t_knowledge_base
    WHERE deleted = 0 AND collection_name LIKE 'hwmall%'
    ORDER BY create_time
    LIMIT 1
) source
WHERE NOT EXISTS (SELECT 1 FROM t_knowledge_base WHERE collection_name = 'hwmallreturn' AND deleted = 0);

CREATE TEMP TABLE tmp_hwmall_intent (
    desired_id VARCHAR(20) PRIMARY KEY,
    intent_code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    level SMALLINT NOT NULL,
    parent_code VARCHAR(64),
    description VARCHAR(512),
    examples TEXT,
    collection_names JSONB NOT NULL,
    mcp_tool_id VARCHAR(128),
    kind SMALLINT NOT NULL,
    prompt_snippet TEXT,
    param_prompt_template TEXT,
    sort_order INTEGER NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_hwmall_intent VALUES
('2608061000000000001','hwmall','华为商城导购',0,NULL,'覆盖稳定商品知识、选购决策、鸿蒙兼容、使用指南、商城公开页面搜索和服务政策；不处理个人订单、账户、支付或售后工单。','[]','[]',NULL,0,NULL,NULL,0),
('2608061000000000002','hwmall-buying','选购决策',1,'hwmall','预算与场景推荐、商品稳定信息对比，以及华为商城公开商品页查找。','[]','[]',NULL,0,NULL,NULL,10),
('2608061000000000003','kb-buying-decision','场景化推荐',2,'hwmall-buying','根据预算、用途、偏好和生态搭配给出候选方向。','["预算 5000 元推荐拍照好的华为手机","办公学习适合买哪款 MateBook"]','["hwmallguide","hwmallproduct"]',NULL,0,'优先使用知识库明确资料回答；价格、库存、优惠等动态快照仅在同一商品/配置且采集时间、地区和有效期明确时转述，证据不足时再回退 MCP；不得回答个人业务数据。',NULL,11),
('2608061000000000004','mcp-vmall-search','商品页面搜索',2,'hwmall-buying','查找公开的华为商城商品页、商品列表或在售入口；搜索摘要存在索引延迟。','["帮我找 Sound X5 的华为商城页面"]','[]','tencent_search',2,'公开网页搜索线索，不是实时业务数据。','保留商品名、型号、配置和限制条件，只输出 JSON；固定 search_type=product_search，不得编造商品标识或交易事实。',12),
('2608061000000000005','kb-product-comparison','商品对比',2,'hwmall-buying','比较多款硬件的稳定参数、卖点、场景差异和选购取舍。','["Mate 80 和 Pura 90 Pro 怎么选"]','["hwmallguide","hwmallproduct"]',NULL,0,'优先使用知识库明确资料回答；价格、库存、优惠等动态快照仅在同一商品/配置且采集时间、地区和有效期明确时转述，证据不足时再回退 MCP；不得回答个人业务数据。',NULL,13),
('2608061000000000006','hwmall-product-knowledge','商品知识',1,'hwmall','稳定参数与卖点、鸿蒙生态兼容，以及使用和通用品类选购知识。','[]','[]',NULL,0,NULL,NULL,20),
('2608061000000000007','kb-product-parameters','参数与卖点',2,'hwmall-product-knowledge','13 类华为硬件的产品家族、稳定规格、功能和核心卖点；排除价格、库存、优惠和动态 SKU。','["HUAWEI WATCH 5 有哪些卖点","Pura 90 Pro 的影像参数是什么"]','["hwmallproduct"]',NULL,0,'优先使用知识库明确资料回答；价格、库存、优惠等动态快照仅在同一商品/配置且采集时间、地区和有效期明确时转述，证据不足时再回退 MCP；不得回答个人业务数据。',NULL,21),
('2608061000000000008','kb-harmony-compatibility','鸿蒙生态兼容',2,'hwmall-product-knowledge','连接、配对、投屏、协议、系统版本、超级终端、华为分享和配件适配规则。','["Sound X5 怎么连接手机","FreeBuds 能同时连接哪些设备"]','["hwmallcompat"]',NULL,0,'具体兼容结果需对应型号、系统和软件版本。',NULL,22),
('2608061000000000009','kb-usage-guide','使用与选购指南',2,'hwmall-product-knowledge','上手、设置、迁移、保养、清洁、重置、配件使用和通用品类选购方法。','["华为路由器怎么重置"]','["hwmallguide"]',NULL,0,'仅使用稳定知识回答。',NULL,23),
('2608061000000000010','hwmall-current-info','商城动态信息',1,'hwmall','公开网页中的价格、在售、优惠、分期和以旧换新线索；不是商城实时业务接口。','[]','[]',NULL,2,'所有结果必须标注公开网页搜索和时效边界。',NULL,30),
('2608061000000000011','mcp-price-stock','价格与在售信息线索',2,'hwmall-current-info','检索公开页面中的当前标价和在售状态线索，不保证实时价格、地区库存或结算价。','["Mate 80 Pro 现在页面标价多少"]','[]','tencent_search',2,'不得把搜索摘要描述为实时库存。','保留型号、配置、地区和商品标识，只输出 JSON；固定 search_type=price_stock，不得编造价格或库存。',31),
('2608061000000000012','mcp-promotion','商城优惠页面',2,'hwmall-current-info','查找当前公开活动页、赠品、优惠券和活动时间说明。','["Pura 系列现在有什么优惠页面"]','[]','tencent_search',2,'活动线索必须保留页面时效边界。','保留商品、品类、活动名和时间条件，只输出 JSON；固定 search_type=promotion，不得编造优惠。',32),
('2608061000000000013','mcp-installment-tradein','分期与以旧换新页面',2,'hwmall-current-info','查找公开分期说明、免息页面和以旧换新入口；不估算个人回收价或资格。','["MateBook 14 有几期免息页面"]','[]','tencent_search',2,'不得承诺个人资格或回收价。','保留新旧商品和分期条件，只输出 JSON；固定 search_type=installment_tradein，不得编造个人资格。',33),
('2608061000000000014','hwmall-service-policy','服务政策',1,'hwmall','配送安装、退货换货退款、保修维修和发票等公开政策；不处理个人工单。','[]','[]',NULL,0,NULL,NULL,40),
('2608061000000000015','kb-delivery-installation','配送与安装',2,'hwmall-service-policy','配送范围、公开时效说明、签收、安装和上门服务边界。','["智慧屏支持上门安装吗"]','["hwmalldelivery"]',NULL,0,'个人订单物流不属于本节点。',NULL,41),
('2608061000000000016','kb-return-refund','退货换货与退款',2,'hwmall-service-policy','退换货条件、申请材料、退款方式和一般处理周期。','["华为商城退货需要什么条件"]','["hwmallreturn"]',NULL,0,'只说明公开政策，不承诺个案结果。',NULL,42),
('2608061000000000017','kb-warranty-repair','保修与维修',2,'hwmall-service-policy','保修期、凭证、非保情形、寄修维修、电子三包凭证和服务权益。','["手机保修需要哪些凭证"]','["hwmallwarranty"]',NULL,0,'只说明公开政策，不处理个人工单。',NULL,43),
('2608061000000000018','kb-invoice','发票',2,'hwmall-service-policy','华为商城发票开具、换开和核对规则。','["电子发票如何开具"]','["hwmallinvoice"]',NULL,0,'只说明公开规则。',NULL,44),
('2608061000000000019','hwmall-system','系统交互',1,'hwmall','问候、能力介绍和服务边界说明。','[]','[]',NULL,1,NULL,NULL,50),
('2608061000000000020','sys-welcome-capabilities','欢迎与能力介绍',2,'hwmall-system','问候、身份和能力范围介绍。','["你好","你能帮我做什么"]','[]',NULL,1,NULL,NULL,51),
('2608061000000000021','sys-service-boundary','服务边界',2,'hwmall-system','说明数据时效、支持品类以及无法执行的账户、订单、支付、物流和工单操作。','["能查我的订单物流吗"]','[]',NULL,1,NULL,NULL,52);

-- 更新同编码节点，保留其数据库主键和历史审计关联。
UPDATE t_intent_node node
SET name = target.name,
    level = target.level,
    parent_code = target.parent_code,
    description = target.description,
    examples = target.examples,
    collection_names = target.collection_names,
    collection_name = CASE WHEN jsonb_array_length(target.collection_names) > 0 THEN target.collection_names ->> 0 ELSE NULL END,
    kb_id = CASE WHEN jsonb_array_length(target.collection_names) > 0
                 THEN (SELECT id FROM t_knowledge_base kb WHERE kb.collection_name = target.collection_names ->> 0 AND kb.deleted = 0 LIMIT 1)
                 ELSE NULL END,
    mcp_tool_id = target.mcp_tool_id,
    kind = target.kind,
    prompt_snippet = target.prompt_snippet,
    param_prompt_template = target.param_prompt_template,
    sort_order = target.sort_order,
    enabled = 1,
    deleted = 0,
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
FROM tmp_hwmall_intent target
WHERE node.intent_code = target.intent_code AND node.deleted = 0;

-- 插入新增节点。
INSERT INTO t_intent_node
    (id, kb_id, intent_code, name, level, parent_code, description, examples,
     collection_name, collection_names, mcp_tool_id, kind, prompt_snippet,
     param_prompt_template, sort_order, enabled, create_by, update_by, create_time, update_time, deleted)
SELECT target.desired_id,
       CASE WHEN jsonb_array_length(target.collection_names) > 0
            THEN (SELECT id FROM t_knowledge_base kb WHERE kb.collection_name = target.collection_names ->> 0 AND kb.deleted = 0 LIMIT 1)
            ELSE NULL END,
       target.intent_code, target.name, target.level, target.parent_code, target.description, target.examples,
       CASE WHEN jsonb_array_length(target.collection_names) > 0 THEN target.collection_names ->> 0 ELSE NULL END,
       target.collection_names, target.mcp_tool_id, target.kind, target.prompt_snippet,
       target.param_prompt_template, target.sort_order, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM tmp_hwmall_intent target
WHERE NOT EXISTS (
    SELECT 1 FROM t_intent_node existing
    WHERE existing.intent_code = target.intent_code AND existing.deleted = 0
);

-- 仅停用华为商城子树中 v3 已移除的旧主题，不影响其他领域。
WITH RECURSIVE hwmall_tree AS (
    SELECT id, intent_code FROM t_intent_node WHERE intent_code = 'hwmall' AND deleted = 0
    UNION ALL
    SELECT child.id, child.intent_code
    FROM t_intent_node child
    JOIN hwmall_tree parent ON child.parent_code = parent.intent_code
    WHERE child.deleted = 0
)
UPDATE t_intent_node node
SET deleted = 1, enabled = 0, update_by = 'system', update_time = CURRENT_TIMESTAMP
WHERE node.id IN (SELECT id FROM hwmall_tree)
  AND node.intent_code NOT IN (SELECT intent_code FROM tmp_hwmall_intent);

-- 所有 KB 叶子必须成功绑定 Collection，否则阻止提交。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM t_intent_node node
        JOIN tmp_hwmall_intent target ON target.intent_code = node.intent_code
        WHERE node.deleted = 0 AND node.level = 2 AND node.kind = 0
          AND (jsonb_array_length(node.collection_names) = 0 OR node.kb_id IS NULL)
    ) THEN
        RAISE EXCEPTION '存在未绑定 Collection 的华为商城 KB 叶子节点';
    END IF;
END $$;

COMMIT;
