param(
    [string]$KnowledgeRoot = "",
    [string]$OutputPath = "",
    [int]$PageSize = 20,
    [int]$DelayMilliseconds = 120
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($KnowledgeRoot)) {
    $KnowledgeRoot = Join-Path $repoRoot "resources\docs\knowledge"
}
$KnowledgeRoot = (Resolve-Path -LiteralPath $KnowledgeRoot).Path
$catalogPath = Join-Path $KnowledgeRoot "_meta\product-catalog.json"
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $KnowledgeRoot "_meta\vmall-product-snapshots.json"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$collectedAt = [DateTimeOffset]::Now.ToString("yyyy-MM-ddTHH:mm:sszzz")
$catalogJson = [System.IO.File]::ReadAllText($catalogPath, [System.Text.Encoding]::UTF8)
$catalog = ConvertFrom-Json -InputObject $catalogJson
$productIdOverrides = @{
    "product-wearable-ultimate-design-diamond" = "10086187155216"
}
$searchKeywordOverrides = @{
    "product-wearable-ultimate-design-diamond" = "HUAWEI WATCH ULTIMATE DESIGN"
}

Add-Type -AssemblyName System.Net.Http

function Normalize-ProductText([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return "" }
    $normalized = $Value.Normalize([Text.NormalizationForm]::FormKC).ToLowerInvariant()
    $normalized = [regex]::Replace($normalized, '(参数与卖点|产品档案|华为商城)', '')
    $normalized = [regex]::Replace($normalized, '(huawei|华为)', '')
    return [regex]::Replace($normalized, '[^\p{L}\p{Nd}]', '')
}

function Get-SearchKeyword([object]$Product) {
    $title = [string]$Product.title
    $title = [regex]::Replace($title, '\s*(参数与卖点|产品档案)\s*$', '')
    return $title.Trim()
}

function Get-ProductScore([string]$Keyword, [object]$Candidate, [string]$KnownPrdId) {
    if ($KnownPrdId -and [string]$Candidate.productId -eq $KnownPrdId) { return 1000.0 }

    $query = Normalize-ProductText $Keyword
    $name = Normalize-ProductText ([string]$Candidate.name)
    $brief = Normalize-ProductText ([string]$Candidate.briefName)
    $sku = Normalize-ProductText ([string]$Candidate.skuName)
    $combined = "$name$brief$sku"
    $score = 0.0

    if ($query -and $query -eq $name) { $score = [Math]::Max($score, 200.0) }
    if ($query -and $query -eq $brief) { $score = [Math]::Max($score, 195.0) }
    if ($query -and $sku.StartsWith($query)) { $score = [Math]::Max($score, 185.0) }
    if ($query -and $name.Contains($query)) { $score = [Math]::Max($score, 175.0) }
    if ($query -and $brief.Contains($query)) { $score = [Math]::Max($score, 170.0) }
    if ($query -and $combined.Contains($query)) { $score = [Math]::Max($score, 160.0) }
    if ($query -and $query.Contains($name) -and $name.Length -ge 5) { $score = [Math]::Max($score, 140.0) }
    if ($query -and $query.Contains($brief) -and $brief.Length -ge 4) { $score = [Math]::Max($score, 135.0) }

    $queryTokens = @([regex]::Matches($Keyword.ToLowerInvariant(), '[a-z]+|\d+(?:\.\d+)?|[\u4e00-\u9fff]+') | ForEach-Object Value)
    foreach ($token in $queryTokens) {
        $tokenNormalized = Normalize-ProductText $token
        if ($tokenNormalized.Length -ge 2 -and $combined.Contains($tokenNormalized)) { $score += 2.0 }
    }

    $criticalTokens = @([regex]::Matches($query, '(pro|max|ultra|plus|mini|air|se|x\d+|\d+(?:\.\d+)?|悦享版|尊享版|活力版|新耀版|典藏版|非凡大师|星钻绽放款|紫金款|黄金款|rgb)') | ForEach-Object Value | Select-Object -Unique)
    foreach ($token in $criticalTokens) {
        if (-not $combined.Contains($token)) { $score -= 35.0 }
    }
    return [Math]::Round($score, 2)
}

function Select-SearchCandidate([object]$Product, [object[]]$Candidates, [string]$Keyword, [string]$ResolvedPrdId) {
    $ranked = @($Candidates | ForEach-Object {
        [pscustomobject]@{
            score = Get-ProductScore $Keyword $_ $ResolvedPrdId
            candidate = $_
        }
    } | Sort-Object score -Descending)

    if ($ranked.Count -eq 0) {
        return [pscustomobject]@{ selected = $null; score = 0.0; margin = 0.0; status = "not_found"; alternatives = @() }
    }

    $top = $ranked[0]
    $secondScore = if ($ranked.Count -gt 1) { [double]$ranked[1].score } else { 0.0 }
    $margin = [Math]::Round(([double]$top.score - $secondScore), 2)
    $knownIdMatched = $ResolvedPrdId -and [string]$top.candidate.productId -eq $ResolvedPrdId
    $status = if ($knownIdMatched) {
        "matched_by_prd_id"
    } elseif ($ResolvedPrdId) {
        "existing_prd_id_not_in_search"
    } elseif ([double]$top.score -ge 170 -and $margin -ge 5) {
        "high_confidence"
    } elseif ([double]$top.score -ge 140 -and $margin -ge 15) {
        "review_recommended"
    } else {
        "ambiguous"
    }

    $alternatives = @($ranked | Select-Object -First 5 | ForEach-Object {
        [ordered]@{
            score = $_.score
            product_id = [string]$_.candidate.productId
            name = [string]$_.candidate.name
            brief_name = [string]$_.candidate.briefName
            sku_code = [string]$_.candidate.skuCode
            sku_name = [string]$_.candidate.skuName
        }
    })
    return [pscustomobject]@{
        selected = $top.candidate
        score = [double]$top.score
        margin = $margin
        status = $status
        alternatives = $alternatives
    }
}

function Convert-SelectedProduct([object]$Candidate) {
    if ($null -eq $Candidate) { return $null }
    $activities = @($Candidate.activityPromInfoList | ForEach-Object {
        [ordered]@{
            type = $_.type
            promotion_tag = [string]$_.promotionTag
            promotion_words = [string]$_.promotionWords
        }
    })
    return [ordered]@{
        product_id = [string]$Candidate.productId
        name = [string]$Candidate.name
        brief_name = [string]$Candidate.briefName
        sku_code = [string]$Candidate.skuCode
        sku_name = [string]$Candidate.skuName
        sku_count = $Candidate.skuCount
        list_price = $Candidate.price
        promotional_price = $Candidate.promoPrice
        estimated_price = $Candidate.estPrice
        promotion_summary = [string]$Candidate.promotionInfo
        activity_promotions = $activities
        button_mode = [string]$Candidate.buttonMode
        inventory_signal = $Candidate.isInv
        good_rate = [string]$Candidate.goodRate
        rating_count = $Candidate.rateCount
        shop_name = [string]$Candidate.shopName
        display_tags = [string]$Candidate.displayTags
        product_page_type = $Candidate.productPageType
    }
}

$handler = New-Object System.Net.Http.HttpClientHandler
$handler.CookieContainer = New-Object System.Net.CookieContainer
$handler.UseCookies = $true
$client = New-Object System.Net.Http.HttpClient($handler)
$results = @()
try {
    $client.Timeout = [TimeSpan]::FromSeconds(40)
    $client.DefaultRequestHeaders.Referrer = [Uri]'https://www.vmall.com/'
    $tokenScript = $client.GetStringAsync('https://openapi.vmall.com/csrftoken.js').GetAwaiter().GetResult()
    $csrfToken = [regex]::Match($tokenScript, 'csrftoken\s*=\s*"([a-zA-Z0-9-]+)"').Groups[1].Value
    if ([string]::IsNullOrWhiteSpace($csrfToken)) { throw "华为商城 CSRF 令牌获取失败" }

    $index = 0
    foreach ($product in $catalog) {
        $index++
        $keyword = Get-SearchKeyword $product
        if ($searchKeywordOverrides.ContainsKey([string]$product.doc_id)) {
            $keyword = $searchKeywordOverrides[[string]$product.doc_id]
        }
        $resolvedPrdId = [string]$product.prd_id
        if (-not $resolvedPrdId -and $productIdOverrides.ContainsKey([string]$product.doc_id)) {
            $resolvedPrdId = $productIdOverrides[[string]$product.doc_id]
        }
        Write-Host ("[{0}/{1}] {2}" -f $index, $catalog.Count, $keyword)
        $requestPayload = [ordered]@{
            req = [ordered]@{
                keyword = $keyword
                portal = "1"
                lang = "zh-CN"
                country = "CN"
                personalizeSearch = "1"
                searchFlag = "0"
                searchSortField = 0
                searchSortType = "desc"
                brandType = 0
                pageNum = 1
                pageSize = $PageSize
            }
        }
        $body = $requestPayload | ConvertTo-Json -Depth 6 -Compress
        $message = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, 'https://openapi.vmall.com/mcp/v1/search/queryPrd')
        try {
            $message.Headers.Add('CsrfToken', $csrfToken)
            $message.Headers.Add('X-Requested-With', 'XMLHttpRequest')
            $message.Headers.Add('Origin', 'https://www.vmall.com')
            $message.Content = New-Object System.Net.Http.StringContent($body, [Text.Encoding]::UTF8, 'application/json')
            $response = $client.SendAsync($message).GetAwaiter().GetResult()
            $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if (-not $response.IsSuccessStatusCode) { throw "HTTP $([int]$response.StatusCode): $responseBody" }
            $payload = $responseBody | ConvertFrom-Json
            if (-not $payload.success) { throw "queryPrd failed: $($payload.resultCode) $($payload.info)" }
            $candidates = @($payload.resultList | Where-Object { $null -ne $_ })
            $selection = Select-SearchCandidate $product $candidates $keyword $resolvedPrdId
            $selected = $selection.selected
            if ($selection.status -eq "existing_prd_id_not_in_search") { $selected = $null }
            $finalPrdId = $resolvedPrdId
            if (-not $finalPrdId -and $null -ne $selected) { $finalPrdId = [string]$selected.productId }
            $results += [ordered]@{
                category = [string]$product.category
                doc_id = [string]$product.doc_id
                title = [string]$product.title
                path = [string]$product.path
                existing_prd_id = [string]$product.prd_id
                resolved_prd_id = $finalPrdId
                search_keyword = $keyword
                match_status = $selection.status
                match_score = $selection.score
                match_margin = $selection.margin
                selected = Convert-SelectedProduct $selected
                alternatives = $selection.alternatives
            }
        } catch {
            $results += [ordered]@{
                category = [string]$product.category
                doc_id = [string]$product.doc_id
                title = [string]$product.title
                path = [string]$product.path
                existing_prd_id = [string]$product.prd_id
                resolved_prd_id = $resolvedPrdId
                search_keyword = $keyword
                match_status = "request_failed"
                match_score = 0.0
                match_margin = 0.0
                selected = $null
                alternatives = @()
                error = $_.Exception.Message
            }
        } finally {
            $message.Dispose()
        }
        if ($DelayMilliseconds -gt 0) { Start-Sleep -Milliseconds $DelayMilliseconds }
    }
} finally {
    $client.Dispose()
    $handler.Dispose()
}

$document = [ordered]@{
    schema_version = 1
    collected_at = $collectedAt
    source = "https://www.vmall.com/"
    api = "https://openapi.vmall.com/mcp/v1/search/queryPrd"
    applicable_region = "CN"
    products = $results
}
[System.IO.File]::WriteAllText($OutputPath, (($document | ConvertTo-Json -Depth 12) + "`n"), $utf8NoBom)

$statusSummary = $results | Group-Object { $_['match_status'] } | Sort-Object Name
Write-Host ""
Write-Host "商品检索快照已写入: $OutputPath"
foreach ($status in $statusSummary) { Write-Host ("- {0}: {1}" -f $status.Name, $status.Count) }
