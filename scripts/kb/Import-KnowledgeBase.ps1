param(
    [string]$BaseUrl = "https://yiersan218.fun/api/ragent",
    [string]$KnowledgeRoot = "",
    [System.Management.Automation.PSCredential]$Credential,
    [string[]]$CollectionName = @(),
    [switch]$Execute,
    [switch]$StartChunk,
    [switch]$WaitForChunk,
    [int]$ChunkTimeoutSeconds = 600,
    [int]$PollIntervalSeconds = 2,
    [int]$DelayMilliseconds = 150
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($KnowledgeRoot)) {
    $KnowledgeRoot = Join-Path $repoRoot "resources\docs\knowledge"
}
$KnowledgeRoot = (Resolve-Path -LiteralPath $KnowledgeRoot).Path
$BaseUrl = $BaseUrl.TrimEnd('/')
if ($WaitForChunk -and -not $StartChunk) {
    throw "-WaitForChunk requires -StartChunk"
}
if ($ChunkTimeoutSeconds -le 0) { throw "ChunkTimeoutSeconds must be greater than zero" }
if ($PollIntervalSeconds -le 0) { throw "PollIntervalSeconds must be greater than zero" }

function Wait-DocumentChunk {
    param(
        [string]$ApiBaseUrl,
        [hashtable]$RequestHeaders,
        [string]$DocumentId,
        [string]$DocumentName,
        [int]$TimeoutSeconds,
        [int]$IntervalSeconds
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $documentResponse = Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/knowledge-base/docs/$DocumentId" -Headers $RequestHeaders
        if ([string]$documentResponse.code -ne "0") {
            throw "Cannot query document status: $($documentResponse.message)"
        }
        $status = ([string]$documentResponse.data.status).ToLowerInvariant()
        if ($status -eq "success") {
            Write-Output "SUCCESS $DocumentName chunks=$($documentResponse.data.chunkCount)"
            return
        }
        if ($status -eq "failed") {
            $errorMessage = ""
            try {
                $logResponse = Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/knowledge-base/docs/$DocumentId/chunk-logs?current=1&size=1" -Headers $RequestHeaders
                $errorMessage = [string](@($logResponse.data.records) | Select-Object -First 1).errorMessage
            } catch {
                $errorMessage = "Unable to load chunk error details"
            }
            throw "Chunk failed: $DocumentName; $errorMessage"
        }
        Start-Sleep -Seconds $IntervalSeconds
    }
    throw "Chunk timed out after $TimeoutSeconds seconds: $DocumentName"
}

& (Join-Path $PSScriptRoot "Build-KnowledgeMetadata.ps1") -KnowledgeRoot $KnowledgeRoot
$config = Get-Content (Join-Path $KnowledgeRoot "_meta\kb-import-config.json") -Encoding UTF8 -Raw | ConvertFrom-Json
$mappings = @($config.collections)
if ($CollectionName.Count -gt 0) {
    $requestedCollections = @{}
    foreach ($name in $CollectionName) {
        if (-not [string]::IsNullOrWhiteSpace($name)) {
            $requestedCollections[$name.Trim()] = $true
        }
    }
    $mappings = @($mappings | Where-Object { $requestedCollections.ContainsKey([string]$_.collection_name) })
    $missingCollections = @($requestedCollections.Keys | Where-Object {
        $name = $_
        -not ($mappings | Where-Object { $_.collection_name -eq $name })
    })
    if ($missingCollections.Count -gt 0) {
        throw "Unknown collection name(s): $($missingCollections -join ', ')"
    }
}

$plan = foreach ($mapping in $mappings) {
    $directory = Join-Path $KnowledgeRoot $mapping.directory
    [pscustomobject]@{
        Collection = $mapping.collection_name
        IntentNode = $mapping.intent_node
        Directory = $mapping.directory
        Documents = @(Get-ChildItem -LiteralPath $directory -Recurse -File -Filter "*.md").Count
    }
}
$plan | Format-Table -AutoSize
Write-Output "Total documents: $(($plan | Measure-Object Documents -Sum).Sum)"

if (-not $Execute) {
    Write-Output "Dry run only. Use -Execute to upload; add -StartChunk to start asynchronous vectorization."
    return
}

if ($null -eq $Credential) {
    $Credential = Get-Credential -Message "Ragent administrator credentials"
}
$plainPassword = $Credential.GetNetworkCredential().Password
$loginBody = @{ username = $Credential.UserName; password = $plainPassword } | ConvertTo-Json
$loginResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" -Body $loginBody
if ([string]$loginResponse.code -ne "0" -or [string]::IsNullOrWhiteSpace([string]$loginResponse.data.token)) {
    throw "Login failed: $($loginResponse.message)"
}
$token = [string]$loginResponse.data.token
$headers = @{ Authorization = $token }

$kbResponse = Invoke-RestMethod -Method Get -Uri "$BaseUrl/knowledge-base?current=1&size=100" -Headers $headers
if ([string]$kbResponse.code -ne "0") { throw "Cannot load knowledge bases: $($kbResponse.message)" }
$knowledgeBases = @($kbResponse.data.records)

Add-Type -AssemblyName System.Net.Http
$httpClient = New-Object System.Net.Http.HttpClient
$httpClient.DefaultRequestHeaders.Add("Authorization", $token)
$chunkConfig = '{"targetChars":1200,"maxChars":1600,"minChars":400,"overlapChars":150}'
$uploaded = 0
$skipped = 0
$started = 0

try {
    foreach ($mapping in $mappings) {
        $kb = $knowledgeBases | Where-Object { $_.collectionName -eq $mapping.collection_name } | Select-Object -First 1
        if ($null -eq $kb) { throw "Missing collection: $($mapping.collection_name)" }

        $existingResponse = Invoke-RestMethod -Method Get -Uri "$BaseUrl/knowledge-base/$($kb.id)/docs?current=1&size=1000" -Headers $headers
        if ([string]$existingResponse.code -ne "0") { throw "Cannot load documents for $($mapping.collection_name)" }
        $existingDocuments = @{}
        foreach ($existing in @($existingResponse.data.records)) {
            $existingDocuments[[string]$existing.docName] = $existing
        }

        $directory = Join-Path $KnowledgeRoot $mapping.directory
        foreach ($file in Get-ChildItem -LiteralPath $directory -Recurse -File -Filter "*.md" | Sort-Object FullName) {
            if ($existingDocuments.ContainsKey($file.Name)) {
                $existing = $existingDocuments[$file.Name]
                $skipped++
                $existingStatus = ([string]$existing.status).ToLowerInvariant()
                if ($StartChunk -and $existingStatus -in @("pending", "failed")) {
                    $chunkResponse = $httpClient.PostAsync("$BaseUrl/knowledge-base/docs/$($existing.id)/chunk", $null).GetAwaiter().GetResult()
                    $chunkText = $chunkResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                    if (-not $chunkResponse.IsSuccessStatusCode) { throw "Chunk HTTP $([int]$chunkResponse.StatusCode): $chunkText" }
                    $chunkPayload = $chunkText | ConvertFrom-Json
                    if ([string]$chunkPayload.code -ne "0") { throw "Chunk API error: $($chunkPayload.message)" }
                    $started++
                    Write-Output "RESUME $($mapping.collection_name) $($file.Name) status=$existingStatus"
                    if ($WaitForChunk) {
                        Wait-DocumentChunk -ApiBaseUrl $BaseUrl -RequestHeaders $headers -DocumentId ([string]$existing.id) -DocumentName $file.Name -TimeoutSeconds $ChunkTimeoutSeconds -IntervalSeconds $PollIntervalSeconds
                    }
                } elseif ($StartChunk -and $WaitForChunk -and $existingStatus -eq "running") {
                    Write-Output "WAIT $($mapping.collection_name) $($file.Name) status=$existingStatus"
                    Wait-DocumentChunk -ApiBaseUrl $BaseUrl -RequestHeaders $headers -DocumentId ([string]$existing.id) -DocumentName $file.Name -TimeoutSeconds $ChunkTimeoutSeconds -IntervalSeconds $PollIntervalSeconds
                } else {
                    Write-Output "SKIP $($mapping.collection_name) $($file.Name) status=$existingStatus"
                }
                continue
            }

            $multipart = New-Object System.Net.Http.MultipartFormDataContent
            $stream = [System.IO.File]::OpenRead($file.FullName)
            $fileContent = New-Object System.Net.Http.StreamContent($stream)
            $fileContent.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("text/markdown")
            [void]$multipart.Add($fileContent, "file", $file.Name)
            [void]$multipart.Add((New-Object System.Net.Http.StringContent("file")), "sourceType")
            [void]$multipart.Add((New-Object System.Net.Http.StringContent("chunk")), "processMode")
            [void]$multipart.Add((New-Object System.Net.Http.StringContent("structure_aware")), "chunkStrategy")
            [void]$multipart.Add((New-Object System.Net.Http.StringContent($chunkConfig)), "chunkConfig")
            try {
                $response = $httpClient.PostAsync("$BaseUrl/knowledge-base/$($kb.id)/docs/upload", $multipart).GetAwaiter().GetResult()
                $responseText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                if (-not $response.IsSuccessStatusCode) { throw "HTTP $([int]$response.StatusCode): $responseText" }
                $payload = $responseText | ConvertFrom-Json
                if ([string]$payload.code -ne "0") { throw "API error: $($payload.message)" }
                $docId = [string]$payload.data.id
                $uploaded++
                Write-Output "UPLOAD $($mapping.collection_name) $($file.Name)"

                if ($StartChunk) {
                    $chunkResponse = $httpClient.PostAsync("$BaseUrl/knowledge-base/docs/$docId/chunk", $null).GetAwaiter().GetResult()
                    $chunkText = $chunkResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                    if (-not $chunkResponse.IsSuccessStatusCode) { throw "Chunk HTTP $([int]$chunkResponse.StatusCode): $chunkText" }
                    $chunkPayload = $chunkText | ConvertFrom-Json
                    if ([string]$chunkPayload.code -ne "0") { throw "Chunk API error: $($chunkPayload.message)" }
                    $started++
                    if ($WaitForChunk) {
                        Wait-DocumentChunk -ApiBaseUrl $BaseUrl -RequestHeaders $headers -DocumentId $docId -DocumentName $file.Name -TimeoutSeconds $ChunkTimeoutSeconds -IntervalSeconds $PollIntervalSeconds
                    }
                }
            } finally {
                $multipart.Dispose()
                $stream.Dispose()
            }
            if ($DelayMilliseconds -gt 0) { Start-Sleep -Milliseconds $DelayMilliseconds }
        }
    }
} finally {
    $httpClient.Dispose()
    $plainPassword = $null
    $token = $null
}

Write-Output "Import complete: uploaded=$uploaded skipped=$skipped chunk_started=$started"
