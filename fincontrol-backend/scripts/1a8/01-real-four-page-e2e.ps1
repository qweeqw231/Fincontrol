param(
    [string]$BaseUrl = "http://localhost:8080",
    [long]$UserId = 1,
    [switch]$SkipHealthCheck
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Get-PropertyValue($Object, [string]$Name) {
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Invoke-CurlJson {
    param(
        [string[]]$Arguments,
        [string]$ResponsePath
    )

    $curlArgs = @("--silent", "--show-error", "--output", $ResponsePath, "--write-out", "%{http_code}") + $Arguments
    $httpCode = (& curl.exe @curlArgs)
    if ($LASTEXITCODE -ne 0) {
        throw "curl failed (exit=$LASTEXITCODE): $($Arguments -join ' ')"
    }
    if (-not (Test-Path $ResponsePath)) {
        throw "response file missing: $ResponsePath"
    }
    $raw = Get-Content -Raw -Encoding UTF8 $ResponsePath
    try {
        $body = $raw | ConvertFrom-Json
    } catch {
        throw "response is not JSON (HTTP $httpCode): $raw"
    }
    [pscustomobject]@{
        HttpCode = [int]$httpCode
        Raw = $raw
        Body = $body
    }
}

function Get-CompleteFunds($ParsedAsset) {
    $funds = @()
    $categories = Get-PropertyValue $ParsedAsset "categories"
    if ($null -eq $categories) { return $funds }
    foreach ($category in @($categories)) {
        $categoryFunds = Get-PropertyValue $category "funds"
        if ($null -eq $categoryFunds) { continue }
        foreach ($fund in @($categoryFunds)) {
            $amount = Get-PropertyValue $fund "amount"
            $profit = Get-PropertyValue $fund "profit"
            $holding = Get-PropertyValue $fund "holdingProfit"
            $cumulative = Get-PropertyValue $fund "cumulativeProfit"
            $fundName = [string](Get-PropertyValue $fund "fundName")
            # 1a.8.7 完整判定：amount + (profit 或 holding 或 cumulative) + name 非空
            $hasProfit = ($null -ne $profit) -or ($null -ne $holding) -or ($null -ne $cumulative)
            if ($null -ne $amount -and $hasProfit -and
                -not [string]::IsNullOrWhiteSpace($fundName)) {
                if ($null -eq $holding) { $holding = if ($null -ne $profit) { $profit } else { $cumulative } }
                if ($null -eq $cumulative) { $cumulative = $holding }
                $profitOut = if ($null -ne $profit) { $profit } else { $holding }
                $funds += [pscustomobject]@{
                    categoryName = [string](Get-PropertyValue $category "categoryName")
                    fundName = $fundName
                    amount = [decimal]$amount
                    profit = [decimal]$profitOut
                    holdingProfit = [decimal]$holding
                    cumulativeProfit = [decimal]$cumulative
                }
            }
        }
    }
    return $funds
}

function Get-IncompleteFunds($ParsedAsset) {
    $funds = @()
    $categories = Get-PropertyValue $ParsedAsset "categories"
    if ($null -eq $categories) { return $funds }
    foreach ($category in @($categories)) {
        $categoryFunds = Get-PropertyValue $category "funds"
        if ($null -eq $categoryFunds) { continue }
        foreach ($fund in @($categoryFunds)) {
            $amount = Get-PropertyValue $fund "amount"
            $profit = Get-PropertyValue $fund "profit"
            $holding = Get-PropertyValue $fund "holdingProfit"
            $cumulative = Get-PropertyValue $fund "cumulativeProfit"
            $hasProfit = ($null -ne $profit) -or ($null -ne $holding) -or ($null -ne $cumulative)
            if ($null -eq $amount -or -not $hasProfit) {
                $funds += [pscustomobject]@{
                    categoryName = [string](Get-PropertyValue $category "categoryName")
                    fundName = [string](Get-PropertyValue $fund "fundName")
                    amount = $amount
                    profit = $profit
                    holdingProfit = $holding
                    cumulativeProfit = $cumulative
                }
            }
        }
    }
    return $funds
}

function Compare-FundSet {
    param(
        [object[]]$Expected,
        [object[]]$Actual,
        [string]$Scope
    )

    $errors = New-Object System.Collections.Generic.List[string]
    $expectedByName = @{}
    foreach ($fund in $Expected) { $expectedByName[[string]$fund.fundName] = $fund }
    $actualByName = @{}
    foreach ($fund in $Actual) {
        $name = [string]$fund.fundName
        if ($actualByName.ContainsKey($name)) {
            $errors.Add("$Scope duplicate complete row: $name")
        }
        $actualByName[$name] = $fund
    }

    foreach ($name in $expectedByName.Keys) {
        if (-not $actualByName.ContainsKey($name)) {
            $errors.Add("$Scope missing: $name")
            continue
        }
        $expected = $expectedByName[$name]
        $actual = $actualByName[$name]
        if ([decimal]$expected.amount -ne [decimal]$actual.amount) {
            $errors.Add("$Scope amount mismatch: $name expected=$($expected.amount) actual=$($actual.amount)")
        }
        if ([decimal]$expected.holdingProfit -ne [decimal]$actual.holdingProfit) {
            $errors.Add("$Scope holding_profit mismatch: $name expected=$($expected.holdingProfit) actual=$($actual.holdingProfit)")
        }
        if ([decimal]$expected.cumulativeProfit -ne [decimal]$actual.cumulativeProfit) {
            $errors.Add("$Scope cumulative_profit mismatch: $name expected=$($expected.cumulativeProfit) actual=$($actual.cumulativeProfit)")
        }
    }
    foreach ($name in $actualByName.Keys) {
        if (-not $expectedByName.ContainsKey($name)) {
            $errors.Add("$Scope unexpected: $name")
        }
    }
    return @($errors)
}

$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$repoRoot = (Resolve-Path (Join-Path $backendRoot "..")).Path
$fixturePath = Join-Path $backendRoot "src\test\resources\fixtures\phase1a8-real-four-pages.json"
$samplesRoot = Join-Path $backendRoot "uploads\samples"
$runId = Get-Date -Format "yyyyMMdd_HHmmss"
$outputRoot = Join-Path $repoRoot "docs\test-records\automated-smoke\1a8\api-test-output\$runId-real-four-page"
$ocrDateRoot = Join-Path $repoRoot ("docs\test-records\ocr-results\" + (Get-Date -Format "yyyy-MM-dd"))
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

if (-not $SkipHealthCheck) {
    $healthPath = Join-Path $outputRoot "health.json"
    $health = Invoke-CurlJson -Arguments @("$BaseUrl/actuator/health") -ResponsePath $healthPath
    if ($health.HttpCode -ne 200 -or [string]$health.Body.status -ne "UP") {
        throw "health check failed: HTTP=$($health.HttpCode), body=$($health.Raw)"
    }
}

$fixture = Get-Content -Raw -Encoding UTF8 $fixturePath | ConvertFrom-Json
$failures = New-Object System.Collections.Generic.List[string]
$pageResults = New-Object System.Collections.Generic.List[object]
$actualUnique = @{}

foreach ($page in @($fixture.pages)) {
    $pageId = [string]$page.pageId
    $imagePath = Join-Path $samplesRoot ([string]$page.fileName)
    if (-not (Test-Path $imagePath)) { throw "sample missing: $imagePath" }

    Write-Host "[$pageId] upload $($page.fileName)" -ForegroundColor Cyan
    $uploadResponsePath = Join-Path $outputRoot "$pageId-upload-response.json"
    $upload = Invoke-CurlJson -Arguments @(
        "--header", "X-User-Id: $UserId",
        "--form", "file=@$imagePath",
        "$BaseUrl/api/screenshot/upload"
    ) -ResponsePath $uploadResponsePath
    if ($upload.HttpCode -lt 200 -or $upload.HttpCode -ge 300 -or [int]$upload.Body.code -ne 0) {
        throw "[$pageId] upload failed: HTTP=$($upload.HttpCode), body=$($upload.Raw)"
    }
    $fileId = [string]$upload.Body.data.fileId
    if ([string]::IsNullOrWhiteSpace($fileId)) { throw "[$pageId] upload returned blank fileId" }

    $parseRequestPath = Join-Path $outputRoot "$pageId-parse-request.json"
    $parseResponsePath = Join-Path $outputRoot "$pageId-parse-response.json"
    $requestJson = @{ fileId = $fileId; userId = $UserId } | ConvertTo-Json -Compress
    Write-Utf8NoBom -Path $parseRequestPath -Content $requestJson

    Write-Host "[$pageId] parse fileId=$fileId" -ForegroundColor Cyan
    $parse = Invoke-CurlJson -Arguments @(
        "--header", "X-User-Id: $UserId",
        "--header", "Content-Type: application/json",
        "--data-binary", "@$parseRequestPath",
        "$BaseUrl/api/screenshot/parse"
    ) -ResponsePath $parseResponsePath
    if ($parse.HttpCode -lt 200 -or $parse.HttpCode -ge 300 -or [int]$parse.Body.code -ne 0) {
        throw "[$pageId] parse failed: HTTP=$($parse.HttpCode), body=$($parse.Raw)"
    }

    $expectedComplete = @(Get-CompleteFunds $page.parsedAsset)
    $actualComplete = @(Get-CompleteFunds $parse.Body.data)
    $actualIncomplete = @(Get-IncompleteFunds $parse.Body.data)
    $pageErrors = @(Compare-FundSet -Expected $expectedComplete -Actual $actualComplete -Scope $pageId)
    if ($actualComplete.Count -ne [int]$page.expectedCompleteCount) {
        $pageErrors += "$pageId count mismatch: expected=$($page.expectedCompleteCount) actual=$($actualComplete.Count)"
    }
    # 1a.8.7 total_asset 规则：visible 优先；不可见则 sum-of-complete-funds 兜底；只在 fixture 给定期望时校验
    $expectedPageTotal = Get-PropertyValue $page.parsedAsset "totalAsset"
    $actualPageTotal = Get-PropertyValue $parse.Body.data "totalAsset"
    if ($null -ne $expectedPageTotal) {
        if ($null -eq $actualPageTotal -or
            [math]::Abs([decimal]$actualPageTotal - [decimal]$expectedPageTotal) -gt [decimal]0.01) {
            $pageErrors += "$pageId totalAsset mismatch: expected=$expectedPageTotal actual=$actualPageTotal"
        }
    } else {
        # 不可见期望时使用 sum-of-complete-funds 兜底（模型若 visible 则覆盖为 visible）
        $fallback = ($actualComplete | ForEach-Object { $_.amount } | Measure-Object -Sum).Sum
        if ($null -ne $fallback -and $null -ne $actualPageTotal -and
            [math]::Abs([decimal]$fallback - [decimal]$actualPageTotal) -gt [decimal]0.01) {
            $pageErrors += "$pageId totalAsset fallback mismatch: actual=$actualPageTotal expected_sum=$fallback"
        }
    }

    foreach ($fund in $actualComplete) { $actualUnique[$fund.fundName] = $fund }
    foreach ($failureMessage in $pageErrors) { $failures.Add($failureMessage) }

    $ocrMatches = @()
    if (Test-Path $ocrDateRoot) {
        $ocrMatches = @(Get-ChildItem -Path $ocrDateRoot -File | Where-Object { $_.Name -like "*$fileId*.json" })
    }
    if ($ocrMatches.Count -lt 1) {
        $failures.Add("$pageId OCR log missing for fileId=$fileId")
    }

    $pageResults.Add([pscustomobject]@{
        pageId = $pageId
        fileName = [string]$page.fileName
        fileId = $fileId
        conversationId = [string]$parse.Body.data.conversationId
        expectedCompleteCount = [int]$page.expectedCompleteCount
        actualCompleteCount = $actualComplete.Count
        incompleteRows = $actualIncomplete
        totalAsset = $actualPageTotal
        ocrFiles = @($ocrMatches | ForEach-Object { $_.FullName })
        errors = $pageErrors
    })
}

$expectedUnique = @($fixture.expectedUnique)
$actualUniqueList = @($actualUnique.Values)
$aggregateErrors = @(Compare-FundSet -Expected $expectedUnique -Actual $actualUniqueList -Scope "AGGREGATE")
if ($actualUniqueList.Count -ne [int]$fixture.expectedUniqueCount) {
    $aggregateErrors += "AGGREGATE count mismatch: expected=$($fixture.expectedUniqueCount) actual=$($actualUniqueList.Count)"
}
$actualTotal = ($actualUniqueList | ForEach-Object { [decimal]$_.amount } | Measure-Object -Sum).Sum
if ([math]::Abs([decimal]$actualTotal - [decimal]$fixture.expectedTotalAsset) -gt [decimal]0.01) {
    $aggregateErrors += "AGGREGATE total mismatch: expected=$($fixture.expectedTotalAsset) actual=$actualTotal"
}
foreach ($failureMessage in $aggregateErrors) { $failures.Add($failureMessage) }

$summary = [pscustomobject]@{
    runId = $runId
    baseUrl = $BaseUrl
    userId = $UserId
    fixtureVersion = [string]$fixture.fixtureVersion
    pages = $pageResults
    aggregate = [pscustomobject]@{
        expectedUniqueCount = [int]$fixture.expectedUniqueCount
        actualUniqueCount = $actualUniqueList.Count
        expectedTotalAsset = [decimal]$fixture.expectedTotalAsset
        actualTotalAsset = [decimal]$actualTotal
        errors = $aggregateErrors
    }
    passed = ($failures.Count -eq 0)
    failures = @($failures)
}
$summaryPath = Join-Path $outputRoot "summary.json"
Write-Utf8NoBom -Path $summaryPath -Content ($summary | ConvertTo-Json -Depth 12)

Write-Host "summary=$summaryPath"
Write-Host "unique=$($actualUniqueList.Count)/$($fixture.expectedUniqueCount), total=$actualTotal/$($fixture.expectedTotalAsset)"
if ($failures.Count -gt 0) {
    Write-Host "DATA CHECK FAILED ($($failures.Count))" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    exit 2
}

Write-Host "A8V3-S07~S09 PASS" -ForegroundColor Green
exit 0
