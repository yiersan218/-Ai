param(
    [string]$KnowledgeRoot = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($KnowledgeRoot)) {
    $KnowledgeRoot = Join-Path $repoRoot "resources\docs\knowledge"
}
$KnowledgeRoot = (Resolve-Path -LiteralPath $KnowledgeRoot).Path
$metaRoot = Join-Path $KnowledgeRoot "_meta"
[void](New-Item -ItemType Directory -Path $metaRoot -Force)
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$generatedAt = [DateTimeOffset]::Now.ToString("yyyy-MM-ddTHH:mm:sszzz")

$collectionMap = [ordered]@{
    "01_参数与卖点_KB" = [ordered]@{ intent_node = "参数与卖点 KB"; collection_name = "hwmallproduct" }
    "02_鸿蒙生态兼容_KB" = [ordered]@{ intent_node = "鸿蒙生态兼容 KB"; collection_name = "hwmallcompat" }
    "03_使用与选购指南_KB" = [ordered]@{ intent_node = "使用与选购指南 KB"; collection_name = "hwmallguide" }
    "04_配送与安装_KB" = [ordered]@{ intent_node = "配送与安装 KB"; collection_name = "hwmalldelivery" }
    "05_退货换货与退款_KB" = [ordered]@{ intent_node = "退货换货与退款 KB"; collection_name = "hwmallreturn" }
    "06_保修与维修_KB" = [ordered]@{ intent_node = "保修与维修 KB"; collection_name = "hwmallwarranty" }
    "07_发票_KB" = [ordered]@{ intent_node = "发票 KB"; collection_name = "hwmallinvoice" }
}

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Get-Sha256([string]$Path) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha.ComputeHash([System.IO.File]::ReadAllBytes($Path))
        return ([BitConverter]::ToString($hash)).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Convert-FrontmatterValue([string]$RawValue) {
    $value = $RawValue.Trim()
    if ($value.StartsWith('"') -or $value.StartsWith('[') -or $value.StartsWith('{')) {
        try { return ($value | ConvertFrom-Json) } catch { return $value.Trim('"') }
    }
    if ($value -eq "true") { return $true }
    if ($value -eq "false") { return $false }
    return $value
}

function Read-KnowledgeDocument([System.IO.FileInfo]$File) {
    $content = [System.IO.File]::ReadAllText($File.FullName)
    $match = [regex]::Match($content, '\A---\r?\n(?<header>.*?)\r?\n---(?:\r?\n|\z)', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    $metadata = [ordered]@{}
    if ($match.Success) {
        foreach ($line in [regex]::Split($match.Groups['header'].Value, '\r?\n')) {
            $field = [regex]::Match($line, '^(?<key>[A-Za-z0-9_-]+):\s*(?<value>.*)$')
            if ($field.Success) {
                $metadata[$field.Groups['key'].Value] = Convert-FrontmatterValue $field.Groups['value'].Value
            }
        }
    }
    $fullPath = [System.IO.Path]::GetFullPath($File.FullName)
    if (-not $fullPath.StartsWith($KnowledgeRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "文档不在知识库根目录内: $fullPath"
    }
    $relative = $fullPath.Substring($KnowledgeRoot.Length).TrimStart('\', '/').Replace('\', '/')
    [pscustomobject]@{
        File = $File
        Path = $relative
        Directory = $relative.Split('/')[0]
        Content = $content
        Metadata = $metadata
        HeaderFound = $match.Success
    }
}

function Escape-Csv([object]$Value) {
    if ($null -eq $Value) { return '""' }
    return '"' + $Value.ToString().Replace('"', '""') + '"'
}

$files = foreach ($directory in $collectionMap.Keys) {
    $fullDirectory = Join-Path $KnowledgeRoot $directory
    if (-not (Test-Path -LiteralPath $fullDirectory)) { throw "缺少知识库目录: $fullDirectory" }
    Get-ChildItem -LiteralPath $fullDirectory -Recurse -File -Filter "*.md"
}
$documents = @($files | Sort-Object FullName | ForEach-Object { Read-KnowledgeDocument $_ })

$manifestDocuments = @()
$estimatedChunkTotal = 0
$sources = @()
$products = @()
$allText = New-Object System.Text.StringBuilder
foreach ($document in $documents) {
    $metadata = $document.Metadata
    $characters = $document.Content.Length
    $estimatedChunks = [Math]::Max(1, [Math]::Ceiling($characters / 1200.0))
    $estimatedChunkTotal += $estimatedChunks
    $manifestDocuments += [ordered]@{
        doc_id = $metadata.doc_id
        title = $metadata.title
        intent_node = $metadata.intent_node
        collection_name = $collectionMap[$document.Directory].collection_name
        path = $document.Path
        characters = $characters
        estimated_chunks_1200 = $estimatedChunks
        sha256 = Get-Sha256 $document.File.FullName
    }
    $urlList = @($metadata.source_urls)
    $sources += [ordered]@{
        doc_id = $metadata.doc_id
        intent_node = $metadata.intent_node
        title = $metadata.title
        source_type = $metadata.source_type
        source_urls = ($urlList -join " | ")
        path = $document.Path
    }
    [void]$allText.AppendLine($document.Content)

    if ($metadata.doc_id -match '^product-' -and $metadata.intent_node -eq "参数与卖点 KB") {
        $sourceUrl = if ($urlList.Count -gt 0) { [string]$urlList[0] } else { "" }
        $prd = [regex]::Match($sourceUrl, '(?:\?|&)prdId=([^&]+)').Groups[1].Value
        $sbom = [regex]::Match($sourceUrl, '(?:\?|&)sbomCode=([^&]+)').Groups[1].Value
        $products += [ordered]@{
            category = @($metadata.scope)[0]
            doc_id = $metadata.doc_id
            title = $metadata.title
            path = $document.Path
            prd_id = $prd
            observed_sbom_code = $sbom
            observed_sku_anchor = $(if ($prd -and $sbom) { "$prd+$sbom" } else { "" })
            source_url = $sourceUrl
        }
    }
}

$manifest = [ordered]@{
    domain = "华为商城导购"
    generated_at = $generatedAt
    document_count = $documents.Count
    estimated_chunks_1200 = $estimatedChunkTotal
    documents = $manifestDocuments
}
Write-Utf8NoBom (Join-Path $metaRoot "manifest.json") (($manifest | ConvertTo-Json -Depth 8) + "`n")

$sourceCsv = @('"doc_id","intent_node","title","source_type","source_urls","path"')
foreach ($source in $sources) {
    $sourceCsv += (($source.Keys | ForEach-Object { Escape-Csv $source[$_] }) -join ',')
}
Write-Utf8NoBom (Join-Path $metaRoot "sources.csv") (($sourceCsv -join "`n") + "`n")

Write-Utf8NoBom (Join-Path $metaRoot "product-catalog.json") (($products | ConvertTo-Json -Depth 6) + "`n")
$productCsv = @('"category","doc_id","title","path","prd_id","observed_sbom_code","observed_sku_anchor","source_url"')
foreach ($product in $products) {
    $productCsv += (($product.Keys | ForEach-Object { Escape-Csv $product[$_] }) -join ',')
}
Write-Utf8NoBom (Join-Path $metaRoot "product-catalog.csv") (($productCsv -join "`n") + "`n")

$categoryCounts = [ordered]@{}
foreach ($group in ($products | Group-Object category | Sort-Object Name)) { $categoryCounts[$group.Name] = $group.Count }
$allContent = $allText.ToString()
$cjkCount = [regex]::Matches($allContent, '[\u3400-\u4DBF\u4E00-\u9FFF]').Count
$nonWhitespaceCount = [regex]::Matches($allContent, '\S').Count
$traceableSkuCount = @($products | Where-Object { $_.observed_sku_anchor }).Count
$statistics = [ordered]@{
    generated_at = $generatedAt
    document_count = $documents.Count
    documents_by_collection = [ordered]@{}
    product_profile_count = $products.Count
    product_category_count = $categoryCounts.Count
    product_categories = @($categoryCounts.Keys)
    product_profiles_by_category = $categoryCounts
    cjk_character_count = $cjkCount
    ten_thousand_chinese_characters = [Math]::Round($cjkCount / 10000.0, 2)
    non_whitespace_character_count = $nonWhitespaceCount
    strict_sku_dataset_records = 0
    traceable_observed_sku_anchors = $traceableSkuCount
    sku_metric_note = "严格 SKU、当前价格和库存不写入静态 KB；可追溯锚点仅用于来源审计。"
}
foreach ($directory in $collectionMap.Keys) {
    $statistics.documents_by_collection[$collectionMap[$directory].collection_name] = @($documents | Where-Object Directory -eq $directory).Count
}
Write-Utf8NoBom (Join-Path $metaRoot "statistics.json") (($statistics | ConvertTo-Json -Depth 8) + "`n")

$statsLines = @(
    "# KBpro 统计",
    "",
    "生成时间：$generatedAt",
    "",
    "- 可入库 Markdown：$($documents.Count)",
    "- 产品档案：$($products.Count)",
    "- 产品品类：$($categoryCounts.Count)",
    "- CJK 字符：$cjkCount",
    "- 1200 字符估算分块：$($manifest.estimated_chunks_1200)",
    "",
    "## Collection 文档数",
    "",
    "| Collection | 文档数 |",
    "|---|---:|"
)
foreach ($directory in $collectionMap.Keys) {
    $collection = $collectionMap[$directory].collection_name
    $statsLines += "| ``$collection`` | $($statistics.documents_by_collection[$collection]) |"
}
$statsLines += @("", "动态 SKU、当前价格、真实库存和个人交易数据不计入静态知识库统计。")
Write-Utf8NoBom (Join-Path $metaRoot "statistics.md") (($statsLines -join "`n") + "`n")

$requiredFields = @('doc_id','title','domain','intent_node','scope','source_type','collected_at')
$missingFields = @()
$intentMismatches = @()
$staticDynamicClaims = @()
$forbiddenKbplus = @()
$missingSources = @()
foreach ($document in $documents) {
    foreach ($field in $requiredFields) {
        if (-not $document.HeaderFound -or -not $document.Metadata.Contains($field) -or [string]::IsNullOrWhiteSpace([string]$document.Metadata[$field])) {
            $missingFields += "$($document.Path):$field"
        }
    }
    $expectedIntent = $collectionMap[$document.Directory].intent_node
    if ($document.Metadata.intent_node -ne $expectedIntent) {
        $intentMismatches += "$($document.Path): expected=$expectedIntent actual=$($document.Metadata.intent_node)"
    }
    if ($document.Content -match '(?i)\bkbplus-') { $forbiddenKbplus += $document.Path }
    if ($document.Content -match '在售核验|未标记[“"]?暂时缺货|当前有货') { $staticDynamicClaims += $document.Path }
    if (@($document.Metadata.source_urls).Count -eq 0) { $missingSources += $document.Path }
}
$duplicateIds = @($documents | Group-Object { $_.Metadata.doc_id } | Where-Object Count -gt 1 | ForEach-Object Name)
$validIntentCodes = @(
    'kb-buying-decision','mcp-vmall-search','kb-product-comparison','kb-product-parameters',
    'kb-harmony-compatibility','kb-usage-guide','mcp-price-stock','mcp-promotion',
    'mcp-installment-tradein','kb-delivery-installation','kb-return-refund','kb-warranty-repair',
    'kb-invoice','sys-welcome-capabilities','sys-service-boundary'
)
$intentEvalPath = Join-Path $metaRoot 'intent-eval.jsonl'
$intentEvalInvalid = @()
$intentEvalCount = 0
if (Test-Path -LiteralPath $intentEvalPath) {
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $intentEvalPath -Encoding UTF8) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try {
            $case = $line | ConvertFrom-Json
            $intentEvalCount++
            if ([string]::IsNullOrWhiteSpace([string]$case.query) -or @($case.expected_intents).Count -eq 0) {
                $intentEvalInvalid += "line ${lineNumber}: missing query or expected_intents"
            }
            foreach ($intentCode in @($case.expected_intents)) {
                if ($validIntentCodes -notcontains $intentCode) {
                    $intentEvalInvalid += "line ${lineNumber}: unknown intent $intentCode"
                }
            }
        } catch {
            $intentEvalInvalid += "line ${lineNumber}: invalid JSON"
        }
    }
}
$errors = @($missingFields) + @($intentMismatches) + @($forbiddenKbplus) + @($staticDynamicClaims) + @($duplicateIds) + @($intentEvalInvalid)
$validation = [ordered]@{
    generated_at = $generatedAt
    status = $(if ($errors.Count -eq 0) { "passed" } else { "failed" })
    document_count = $documents.Count
    required_frontmatter_missing = $missingFields
    duplicate_doc_ids = $duplicateIds
    intent_directory_mismatches = $intentMismatches
    forbidden_kbplus_occurrences = $forbiddenKbplus
    static_price_stock_claims = $staticDynamicClaims
    documents_without_source_urls = $missingSources
    intent_eval_cases = $intentEvalCount
    intent_eval_invalid = $intentEvalInvalid
    errors = $errors.Count
    warnings = $missingSources.Count
}
Write-Utf8NoBom (Join-Path $metaRoot "validation.json") (($validation | ConvertTo-Json -Depth 8) + "`n")

$buildLines = @(
    "# KBpro 构建报告",
    "",
    "- 生成时间：$generatedAt",
    "- 校验状态：$($validation.status)",
    "- 文档数：$($documents.Count)",
    "- 产品档案：$($products.Count)",
    "- Collection：$($collectionMap.Count)",
    "- 意图评测样例：$intentEvalCount",
    "- 错误：$($validation.errors)",
    "- 警告：$($validation.warnings)",
    "",
    "清单、来源、产品目录、统计与校验文件均由 ``scripts/kb/Build-KnowledgeMetadata.ps1`` 生成。"
)
Write-Utf8NoBom (Join-Path $metaRoot "build-report.md") (($buildLines -join "`n") + "`n")

Write-Output "KB metadata built: documents=$($documents.Count), products=$($products.Count), errors=$($validation.errors), warnings=$($validation.warnings)"
if ($validation.errors -gt 0) { throw "知识库校验失败，请查看 $metaRoot\validation.json" }
