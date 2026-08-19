param(
    [string]$KnowledgeRoot = "",
    [string]$SnapshotPath = "",
    [string]$DetailCachePath = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($KnowledgeRoot)) {
    $KnowledgeRoot = Join-Path $repoRoot "resources\docs\knowledge"
}
$KnowledgeRoot = (Resolve-Path -LiteralPath $KnowledgeRoot).Path
if ([string]::IsNullOrWhiteSpace($SnapshotPath)) {
    $SnapshotPath = Join-Path $KnowledgeRoot "_meta\vmall-product-snapshots.json"
}
if ([string]::IsNullOrWhiteSpace($DetailCachePath)) {
    $DetailCachePath = Join-Path $KnowledgeRoot "_meta\vmall-product-detail-cache.json"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$nl = "`n"
$snapshotDocument = ConvertFrom-Json -InputObject ([System.IO.File]::ReadAllText($SnapshotPath, [Text.Encoding]::UTF8))
$detailDocument = ConvertFrom-Json -InputObject ([System.IO.File]::ReadAllText($DetailCachePath, [Text.Encoding]::UTF8))
$snapshotDate = ([DateTimeOffset]::Parse([string]$snapshotDocument.collected_at)).ToString("yyyy-MM-dd")
$snapshotTimestamp = [string]$snapshotDocument.collected_at
$snapshotRefreshAfter = ([DateTimeOffset]::Parse([string]$snapshotDocument.collected_at)).AddDays(7).ToString("yyyy-MM-dd")
$valueSeparator = [char]31

$detailsByPrdId = @{}
foreach ($detail in $detailDocument.products) {
    $detailsByPrdId[[string]$detail.product_id] = $detail
}

function Escape-MarkdownCell([object]$Value) {
    if ($null -eq $Value) { return "" }
    $text = [System.Net.WebUtility]::HtmlDecode([string]$Value)
    $text = [regex]::Replace($text, '(?i)<br\s*/?>', '；')
    $text = [regex]::Replace($text, '<[^>]+>', '')
    $text = [regex]::Replace($text, '[\r\n]+', '；')
    $text = [regex]::Replace($text, '\s+', ' ').Trim()
    return $text.Replace('|', '\|')
}

function Format-Money([object]$Value) {
    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) { return "" }
    try { return "¥" + ([decimal]$Value).ToString("0.##") } catch { return [string]$Value }
}

function Format-Range([object[]]$Values) {
    $numbers = @($Values | Where-Object { $null -ne $_ -and -not [string]::IsNullOrWhiteSpace([string]$_) } | ForEach-Object { [decimal]$_ } | Sort-Object -Unique)
    if ($numbers.Count -eq 0) { return "" }
    if ($numbers.Count -eq 1) { return Format-Money $numbers[0] }
    return (Format-Money $numbers[0]) + " ～ " + (Format-Money $numbers[-1])
}

function Get-UniqueText([object[]]$Values) {
    return @($Values | ForEach-Object { Escape-MarkdownCell $_ } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)
}

function New-JsonArrayLine([string[]]$Values) {
    $encoded = @($Values | ForEach-Object { '"' + $_.Replace('\', '\\').Replace('"', '\"') + '"' })
    return '[' + ($encoded -join ', ') + ']'
}

function Write-TextWithRetry([string]$Path, [string]$Content) {
    # 知识库源文件统一使用 LF，避免 Windows PowerShell 在已有 LF 文档中混入 CRLF。
    $Content = [regex]::Replace($Content, "`r`n?|`n", "`n")
    $lastError = $null
    for ($attempt = 1; $attempt -le 12; $attempt++) {
        try {
            [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
            return
        } catch [System.IO.IOException] {
            $lastError = $_
            Start-Sleep -Milliseconds 250
        }
    }
    throw $lastError
}

function Update-Frontmatter([string]$Content, [object]$Detail) {
    $defaultSku = @($Detail.skus | Where-Object { $_.default_sku }) | Select-Object -First 1
    if ($null -eq $defaultSku) { $defaultSku = @($Detail.skus) | Select-Object -First 1 }
    $directUrl = "https://www.vmall.com/product/comdetail/index.html?prdId=$($Detail.product_id)"
    if ($null -ne $defaultSku -and $defaultSku.sbom_code) {
        $directUrl += "&sbomCode=$($defaultSku.sbom_code)"
    }
    $sourceMatch = [regex]::Match($Content, '(?m)^source_urls:\s*(?<value>\[.*\])\s*$')
    $sourceUrls = @()
    if ($sourceMatch.Success) {
        try {
            $parsedSources = ConvertFrom-Json -InputObject $sourceMatch.Groups['value'].Value
            $sourceUrls = @($parsedSources | ForEach-Object { @(([string]$_) -split '\s+') })
        } catch {
            $sourceUrls = @()
        }
    }
    $retainedSources = @($sourceUrls | Where-Object { $_ -notmatch '/portal/search/' -and $_ -notmatch '/product/comdetail/' })
    $newSourceLine = 'source_urls: ' + (New-JsonArrayLine (@($directUrl) + $retainedSources))
    if ($sourceMatch.Success) {
        $Content = $Content.Substring(0, $sourceMatch.Index) + $newSourceLine + $Content.Substring($sourceMatch.Index + $sourceMatch.Length)
    }

    $collectedLine = 'collected_at: "' + $snapshotDate + '"'
    $Content = [regex]::Replace($Content, '(?m)^collected_at:\s*"?[^\r\n"]+"?\s*$', $collectedLine)
    $Content = [regex]::Replace($Content, '(?m)^(dynamic_product_data_via_mcp|dynamic_price_stock_excluded|dynamic_product_snapshot|snapshot_collected_at|snapshot_refresh_after|snapshot_region|mcp_fallback_when_kb_insufficient):.*\r?\n?', '')
    $frontmatter = [regex]::Match($Content, '\A---\r?\n.*?\r?\n---', [Text.RegularExpressions.RegexOptions]::Singleline)
    if (-not $frontmatter.Success) { throw "缺少 frontmatter" }
    $fields = @(
        'dynamic_product_snapshot: true',
        ('snapshot_collected_at: "' + $snapshotTimestamp + '"'),
        ('snapshot_refresh_after: "' + $snapshotRefreshAfter + '"'),
        'snapshot_region: "CN"',
        'mcp_fallback_when_kb_insufficient: true'
    ) -join $nl
    $insertAt = $frontmatter.Index + $frontmatter.Length - 3
    return $Content.Substring(0, $insertAt) + $fields + $nl + $Content.Substring($insertAt)
}

function Update-LegacyBoundaryStatements([string]$Content) {
    $Content = $Content.Replace(
        '颜色、容量、套餐、价格、库存和优惠属于动态信息，不写入知识库。',
        '颜色、容量、套餐及页面价格以本档案带采集时间的官网快照为准；实时库存、最终结算价与个人权益在快照证据不足时再回退 MCP。'
    )
    $Content = $Content.Replace(
        '颜色、容量、套餐、实时价格、库存、优惠、分期和以旧换新资格不写入知识库。',
        '颜色、容量、套餐、页面价格、优惠、分期和以旧换新线索已按采集时间写入官网快照；实时库存、最终结算价与个人资格在证据不足时再回退 MCP。'
    )
    $Content = $Content.Replace(
        '价格、库存、优惠、分期、赠品与以旧换新资格均为动态信息，应由 MCP 实时查询。',
        '价格、优惠、分期、赠品与以旧换新线索优先使用本档案的带时间官网快照；实时库存、最终结算价与个人资格在快照证据不足时再回退 MCP。'
    )
    $Content = $Content.Replace(
        '实时价格、库存、优惠、配送地区、颜色、尺寸、套餐和全部 SKU 组合不写入静态知识库，应由 MCP 查询。',
        '颜色、尺寸、套餐、全部 SKU、页面价格与优惠已按采集时间写入官网快照；实时库存、配送地区、最终结算价与个人权益在快照证据不足时再回退 MCP。'
    )
    $Content = $Content.Replace(
        '- 实时价格、库存、优惠、分期和以旧换新信息必须调用对应 MCP，并以查询当时的商城页面为准。',
        '- 先使用本档案带采集时间的价格、优惠、分期和以旧换新快照；实时库存、最终结算价或快照证据不足时再回退 MCP，并以商城实际结算页为准。'
    )
    $Content = [regex]::Replace(
        $Content,
        '- 价格、库存、颜色(?:、表带)?、套餐、赠品和分期由 MCP 实时查询。',
        '- 颜色、表带或版本、套餐、页面价格、赠品和分期优先使用本档案的带时间官网快照；实时库存、最终结算价或快照证据不足时再回退 MCP。'
    )
    $Content = $Content.Replace(
        '- 商品套装、颜色和可售状态由 MCP 查询',
        '- 商品套装、颜色和页面价格优先使用本档案的带时间官网快照；实时可售状态或快照证据不足时再回退 MCP'
    )
    $Content = $Content.Replace(
        '实时价格、库存、活动和分期必须调用对应 MCP。',
        '页面价格、活动和分期优先使用本档案的带时间官网快照；实时库存、最终结算价或快照证据不足时再回退 MCP。'
    )
    $Content = $Content.Replace(
        '涉及交易态信息时转交 MCP。',
        '涉及快照未覆盖或已过期的交易态信息时再回退 MCP。'
    )
    $Content = $Content.Replace(
        '以上是家族层面的稳定区分；具体尺寸、颜色、套装和当前可售组合由 MCP 查询。',
        '以上是家族层面的稳定区分；具体尺寸、颜色、套装和页面价格优先使用本档案的带时间官网快照，实时可售组合或证据不足时再回退 MCP。'
    )
    return $Content
}

function New-ProductSnapshotSection([object]$Snapshot, [object]$Detail) {
    $skus = @($Detail.skus)
    $defaultSku = @($skus | Where-Object { $_.default_sku }) | Select-Object -First 1
    if ($null -eq $defaultSku) { $defaultSku = $skus | Select-Object -First 1 }
    $searchProduct = $Snapshot.selected
    if ($null -ne $searchProduct -and [string]$searchProduct.product_id -ne [string]$Detail.product_id) {
        $searchProduct = $null
    }
    $directUrl = "https://www.vmall.com/product/comdetail/index.html?prdId=$($Detail.product_id)"

    $lines = @(
        '<!-- VMALL_PRODUCT_SNAPSHOT:START -->',
        "## 华为商城商品基础信息快照（$snapshotDate）",
        '',
        "> 本节来自华为商城中国区公开商品页，采集时间为 $snapshotTimestamp。价格、促销、购买入口和评价会变化；页面标价不等于最终到手价或结算价，地区库存、账号权益和最终可售状态以实际商品页及结算页为准。",
        '',
        '| 字段 | 官网快照 |',
        '|---|---|',
        "| 官网商品名称 | $(Escape-MarkdownCell $Detail.name) |",
        "| 商品编号（prdId） | $(Escape-MarkdownCell $Detail.product_id) |",
        "| 品牌 | $(Escape-MarkdownCell $Detail.brand_name) |",
        "| 销售 SKU 数量 | $($skus.Count) |",
        "| 建议刷新日期 | $snapshotRefreshAfter；超过该日期的时效性问题视为证据不足 |"
    )
    if ($null -ne $defaultSku) {
        $lines += "| 默认 SKU | $(Escape-MarkdownCell $defaultSku.sbom_code)；$(Escape-MarkdownCell $defaultSku.name) |"
    }
    $listPriceRange = Format-Range @($skus | ForEach-Object price)
    if ($listPriceRange) { $lines += "| SKU 页面标价区间（抓取时） | $listPriceRange |" }
    $timedPriceRange = Format-Range @($skus | ForEach-Object { if ($null -ne $_.timed_promotion) { $_.timed_promotion.price } })
    if ($timedPriceRange) { $lines += "| SKU 限时活动价区间（抓取时） | $timedPriceRange |" }
    if ($null -ne $searchProduct) {
        if ($null -ne $searchProduct.promotional_price) {
            $lines += "| 商城搜索列表促销价（抓取时） | $(Format-Money $searchProduct.promotional_price) |"
        }
        if ($searchProduct.shop_name) {
            $lines += "| 店铺 | $(Escape-MarkdownCell $searchProduct.shop_name) |"
        }
        if ($searchProduct.promotion_summary) {
            $lines += "| 官网卖点摘要 | $(Escape-MarkdownCell (($searchProduct.promotion_summary -split '\|') -join '；')) |"
        }
        if ($searchProduct.good_rate -or $null -ne $searchProduct.rating_count) {
            $ratingParts = @()
            if ($searchProduct.good_rate) { $ratingParts += "好评率 $(Escape-MarkdownCell $searchProduct.good_rate)%" }
            if ($null -ne $searchProduct.rating_count) { $ratingParts += "评价数 $(Escape-MarkdownCell $searchProduct.rating_count)" }
            $lines += "| 用户评价快照（抓取时） | $($ratingParts -join '；') |"
        }
    }
    $lines += "| 官网商品页 | $directUrl |"

    $optionDimensions = @($Detail.option_dimensions | Where-Object { $_.name -and @($_.values).Count -gt 0 })
    if ($optionDimensions.Count -gt 0) {
        $lines += @('', '### 可选规格维度', '', '| 维度 | 官网可选项 |', '|---|---|')
        foreach ($dimension in $optionDimensions) {
            $values = Get-UniqueText @($dimension.values)
            $lines += "| $(Escape-MarkdownCell $dimension.name) | $($values -join '、') |"
        }
    }

    $majorNames = Get-UniqueText @($skus | ForEach-Object { $_.major_attributes | ForEach-Object name })
    if ($majorNames.Count -gt 0) {
        $lines += @('', '### 官网核心属性', '', '| 属性 | 官网值 |', '|---|---|')
        foreach ($majorName in $majorNames) {
            $values = Get-UniqueText @($skus | ForEach-Object { $_.major_attributes | Where-Object { $_.name -eq $majorName } | ForEach-Object value })
            $lines += "| $majorName | $($values -join '；') |"
        }
    }

    $lines += @(
        '',
        '### SKU 明细',
        '',
        '| SKU 编码 | 商品/版本 | 配置属性 | 页面标价 | 限时活动价与有效期 | 商品页入口 |',
        '|---|---|---|---:|---|---|'
    )
    foreach ($sku in ($skus | Sort-Object @{Expression={-not $_.default_sku}}, sbom_code)) {
        $skuName = Escape-MarkdownCell $sku.name
        if ($sku.default_sku) { $skuName += '（默认）' }
        $attributes = @($sku.attributes | ForEach-Object { "$(Escape-MarkdownCell $_.name)=$(Escape-MarkdownCell $_.value)" }) -join '；'
        $promotion = ''
        if ($null -ne $sku.timed_promotion -and $null -ne $sku.timed_promotion.price) {
            $promotion = Format-Money $sku.timed_promotion.price
            $promotionParts = Get-UniqueText @($sku.timed_promotion.label, $sku.timed_promotion.word)
            $timeRange = ''
            if ($sku.timed_promotion.start_time -or $sku.timed_promotion.end_time) {
                $timeRange = "$(Escape-MarkdownCell $sku.timed_promotion.start_time) 至 $(Escape-MarkdownCell $sku.timed_promotion.end_time)"
            }
            $promotionDetails = @($promotionParts)
            if ($timeRange) { $promotionDetails += $timeRange }
            if ($promotionDetails.Count -gt 0) { $promotion += "（$($promotionDetails -join '；')）" }
        }
        $entry = if ([string]$sku.button_mode -eq '1') {
            '显示购买入口'
        } elseif ($sku.coming_soon) {
            '页面标记为即将开售'
        } else {
            "页面状态码 $(Escape-MarkdownCell $sku.button_mode)"
        }
        $lines += "| $(Escape-MarkdownCell $sku.sbom_code) | $skuName | $attributes | $(Format-Money $sku.price) | $promotion | $entry |"
    }

    $benefits = @()
    foreach ($sku in $skus) {
        foreach ($benefit in @($sku.benefits)) {
            $title = Escape-MarkdownCell $benefit.title
            $content = Escape-MarkdownCell $benefit.content
            if ($title -or $content) { $benefits += ($title + $valueSeparator + $content) }
        }
    }
    if ($null -ne $searchProduct) {
        foreach ($activity in @($searchProduct.activity_promotions)) {
            $title = Escape-MarkdownCell $activity.promotion_tag
            $content = Escape-MarkdownCell $activity.promotion_words
            if ($title -or $content) { $benefits += ($title + $valueSeparator + $content) }
        }
    }
    $benefits = @($benefits | Sort-Object -Unique)
    if ($benefits.Count -gt 0) {
        $lines += @('', '### 官网权益与活动摘要（抓取时）', '')
        foreach ($benefit in $benefits) {
            $parts = $benefit -split [string]$valueSeparator, 2
            if ($parts[0] -and $parts[1]) { $lines += "- $($parts[0])：$($parts[1])" }
            elseif ($parts[0]) { $lines += "- $($parts[0])" }
            elseif ($parts[1]) { $lines += "- $($parts[1])" }
        }
    }

    $promotionWords = Get-UniqueText @($skus | ForEach-Object promotion_word)
    if ($promotionWords.Count -gt 0) {
        $lines += @('', '### SKU 官方卖点补充', '')
        foreach ($word in $promotionWords) { $lines += "- $word" }
    }

    $installDescriptions = Get-UniqueText @($skus | ForEach-Object install_description)
    if ($installDescriptions.Count -gt 0) {
        $lines += @('', '### 安装说明', '')
        foreach ($description in $installDescriptions) { $lines += "- $description" }
    }

    $lines += @(
        '',
        '### 快照使用边界',
        '',
        '- 本节可用于回答已列出的商品名称、商品编号、SKU、颜色/版本等选项、页面标价、限时活动价、核心属性和公开权益。',
        '- buttonMode 仅反映采集时商品页是否显示购买入口，不等于地区实时库存。页面未给出的到手价、券后价、库存数量、配送时效和个人资格不能据此推断。',
        '- 用户明确询问“现在/此刻”的价格、库存、优惠或结算结果，而本快照已过期或证据不足时，才回退到 MCP 查询，并仍以华为商城实际结算页为准。',
        '<!-- VMALL_PRODUCT_SNAPSHOT:END -->'
    )
    return $lines -join $nl
}

$updated = 0
$skipped = @()
foreach ($snapshot in $snapshotDocument.products) {
    $detail = $detailsByPrdId[[string]$snapshot.resolved_prd_id]
    if ($null -eq $detail) {
        $skipped += [ordered]@{
            doc_id = [string]$snapshot.doc_id
            prd_id = [string]$snapshot.resolved_prd_id
            reason = 'detail_not_available'
        }
        continue
    }
    $filePath = Join-Path $KnowledgeRoot ([string]$snapshot.path).Replace('/', '\')
    if (-not (Test-Path -LiteralPath $filePath)) { throw "知识库文档不存在: $filePath" }
    $content = [System.IO.File]::ReadAllText($filePath, [Text.Encoding]::UTF8)
    $content = [regex]::Replace($content, '(?ms)\r?\n?<!-- VMALL_PRODUCT_SNAPSHOT:START -->.*?<!-- VMALL_PRODUCT_SNAPSHOT:END -->\r?\n?', $nl)
    $content = Update-Frontmatter $content $detail
    $content = Update-LegacyBoundaryStatements $content
    $section = New-ProductSnapshotSection $snapshot $detail
    $heading = [regex]::Match($content, '(?m)^# .+$')
    if (-not $heading.Success) { throw "文档缺少一级标题: $filePath" }
    $insertAt = $heading.Index + $heading.Length
    $content = $content.Substring(0, $insertAt) + $nl + $nl + $section + $nl + $nl + $content.Substring($insertAt).TrimStart([char]13, [char]10)
    $content = [regex]::Replace($content, '(?m)^(# .+)\r?\n(?:[ \t]*\r?\n)+(?=<!-- VMALL_PRODUCT_SNAPSHOT:START -->)', ('$1' + $nl + $nl))
    $content = [regex]::Replace($content, '(?m)^(<!-- VMALL_PRODUCT_SNAPSHOT:END -->)\r?\n(?:[ \t]*\r?\n)*(?=## )', ('$1' + $nl + $nl))
    Write-TextWithRetry $filePath ($content.TrimEnd() + $nl)
    $updated++
}

$catalogUpdated = 0
$catalogRoot = Join-Path $KnowledgeRoot "01_参数与卖点_KB\目录与范围"
foreach ($catalogFile in (Get-ChildItem -LiteralPath $catalogRoot -File -Filter "*.md")) {
    $content = [System.IO.File]::ReadAllText($catalogFile.FullName, [Text.Encoding]::UTF8)
    $content = [regex]::Replace($content, '(?m)^collected_at:\s*"?[^\r\n"]+"?\s*$', ('collected_at: "' + $snapshotDate + '"'))
    $catalogFields = 'product_detail_snapshots_in_kb: true' + $nl + 'mcp_fallback_when_kb_insufficient: true'
    $content = [regex]::Replace($content, '(?m)^dynamic_product_data_via_mcp:\s*true\s*$', $catalogFields)
    $content = [regex]::Replace(
        $content,
        '颜色、容量、尺寸、套餐、当前价格、库存和严格 SKU 由 MCP 实时查询，不以本目录静态统计替代商城状态。',
        '颜色、容量、尺寸、套餐、页面价格和严格 SKU 已写入各产品档案的带时间官网快照；实时库存、最终结算价或快照证据不足时再回退 MCP。'
    )
    $content = $content.Replace(
        '实时价格、库存、优惠、可售配置与严格 SKU 由 MCP 查询。',
        '页面价格、优惠、可售配置与严格 SKU 已写入具体产品档案的带时间官网快照；实时库存、最终结算价或快照证据不足时再回退 MCP。'
    )
    $content = $content.Replace(
        '当前公开价格、在售信息、优惠、可售配置与严格 SKU 不属于静态档案；需要时由 MCP 查询公开网页线索，并以商城实际页面为准。',
        '当前公开页面价格、优惠、可售配置与严格 SKU 已写入具体产品档案的带时间官网快照；实时库存、最终结算价或快照证据不足时再回退 MCP，并以商城实际页面为准。'
    )
    $content = $content.Replace(
        '先用本目录确定候选家族，再按用户预算、场景和约束检索具体档案；涉及实时配置时调用 MCP。目录中的产品不代表每个地区、每个时刻均有货。',
        '先用本目录确定候选家族，再按用户预算、场景和约束检索具体档案；优先使用具体档案中的 SKU、配置与价格快照，实时库存、最终结算价或快照证据不足时再回退 MCP。目录中的产品不代表每个地区、每个时刻均有货。'
    )
    $content = $content.Replace(
        '档案数量按产品家族文档统计，不等于颜色、容量、尺寸、套餐组成的严格 SKU 数量。',
        '档案数量按产品家族文档统计；颜色、容量、尺寸、套餐组成的严格 SKU 数量和明细见各产品档案的官网快照。'
    )
    Write-TextWithRetry $catalogFile.FullName ($content.TrimEnd() + $nl)
    $catalogUpdated++
}

Write-Host "已写入官网商品基础信息快照: $updated 份文档"
Write-Host "未写入详情快照: $($skipped.Count) 份文档"
Write-Host "已更新产品目录边界: $catalogUpdated 份文档"
foreach ($item in $skipped) {
    Write-Host ("- {0} ({1}): {2}" -f $item.doc_id, $item.prd_id, $item.reason)
}
