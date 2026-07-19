# 1a.10 单图真实 E2E（minimax primary）。与 1a.9 单图方法一致：
# 4 张图逐张 upload + 4 次 POST /api/screenshot/parse（每张独立）
# 验证 4/4 都能正确返回各自结果，code=0，parsed 字段有 fund 数据
# 启动后端：Start-Process -FilePath 'C:\Program Files\Java\jdk-17\bin\java.exe' -ArgumentList '-jar','target\fincontrol-backend.jar'

# .NET 5+ PowerShell 默认 UTF-8；旧版 Windows PowerShell 5.1 用 Get-Content -Raw 默认 GBK
# 强制以 UTF-8 读字节 → 字符串，避免中文 JSON 解析失败
function Read-Utf8([string]$Path) {
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

$ErrorActionPreference = "Stop"
$h = "http://localhost:8080"
$u = 18008
$base = "c:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend"
$samples = Join-Path $base "uploads\samples"
$pages = @(
    @{ name = "P1"; file = "phase1a2-alipay-fund-list-20260715-2355-1.jpg"; expectTopVisible = $false; expectCompleteFunds = 6 },
    @{ name = "P2"; file = "phase1a2-alipay-fund-list-20260715-2355-2.jpg"; expectTopVisible = $true;  expectTopAmount = 7884.68; expectCompleteFunds = 3 },
    @{ name = "P3"; file = "phase1a2-alipay-fund-list-20260715-2355-3.jpg"; expectTopVisible = $false; expectCompleteFunds = 5 },
    @{ name = "P4"; file = "phase1a2-alipay-fund-list-20260715-2356-1.jpg"; expectTopVisible = $false; expectCompleteFunds = 6 }
)

$failures = New-Object System.Collections.Generic.List[object]
$results = New-Object System.Collections.Generic.List[object]

foreach ($p in $pages) {
    $path = Join-Path $samples $p.file
    Write-Host "====================================================="
    Write-Host ("=== " + $p.name + " : " + $p.file)
    Write-Host "====================================================="

    # 1) upload
    $uploadPath = Join-Path $base "logs\1a10-single-$($p.name)-upload.body"
    & curl.exe -s -X POST -H "X-User-Id: 1" -F "file=@$path" ($h + "/api/screenshot/upload") -o $uploadPath
    $uploadTxt = Read-Utf8 $uploadPath
    Write-Output ("upload: " + $uploadTxt)
    $upObj = $uploadTxt | ConvertFrom-Json
    if (-not $upObj.data -or -not $upObj.data.fileId) {
        Write-Host ("FAIL upload")
        $failures.Add(@{ page = $p.name; stage = "upload" })
        continue
    }
    $fileId = $upObj.data.fileId
    Write-Host ("upload OK fileId=" + $fileId)

    # 2) parse（POST /api/screenshot/parse，单图 minimax primary）
    $body = (@{ userId = $u; fileId = $fileId } | ConvertTo-Json -Compress)
    $reqPath = Join-Path $base "logs\1a10-single-$($p.name)-body.json"
    [IO.File]::WriteAllText($reqPath, $body, (New-Object System.Text.UTF8Encoding $false))
    $respPath = Join-Path $base "logs\1a10-single-$($p.name)-resp.json"
    & curl.exe -s --data-binary ('@' + $reqPath) -H "X-User-Id: 1" -H "Content-Type: application/json" ($h + "/api/screenshot/parse") -o $respPath
    $parseTxt = Read-Utf8 $respPath
    Write-Output ("parse raw: " + $parseTxt.Substring(0, [Math]::Min(500, $parseTxt.Length)))

    try {
        $parseObj = $parseTxt | ConvertFrom-Json
    } catch {
        Write-Host ("FAIL parse not JSON: " + $parseTxt.Substring(0, 200))
        $failures.Add(@{ page = $p.name; stage = "parse-decode" })
        continue
    }

    if ($parseObj.code -ne 0) {
        Write-Host ("FAIL parse code=" + $parseObj.code + " msg=" + $parseObj.message)
        $failures.Add(@{ page = $p.name; stage = "parse-code" })
        continue
    }

    $parsed = $parseObj.data
    if (-not $parsed) {
        Write-Host "FAIL parse no data"
        $failures.Add(@{ page = $p.name; stage = "parse-nodata" })
        continue
    }

    $categoryCount = 0
    $totalAmount = 0.0
    $fundNames = @()
    if ($parsed.categories) {
        $categoryCount = @($parsed.categories).Count
        foreach ($cat in $parsed.categories) {
            if ($cat.funds) {
                foreach ($f in $cat.funds) {
                    $fundNames += $f.fundName
                    if ($f.amount) { $totalAmount += [double]$f.amount }
                }
            }
        }
    }

    $topValue = $parsed.totalAsset
    $topSource = $parsed.totalAssetSource
    Write-Host ("  parsed.top      = " + $topValue)
    Write-Host ("  parsed.topSource= " + $topSource)
    Write-Host ("  parsed.categories=" + $categoryCount)
    Write-Host ("  parsed.fundCount=" + $fundNames.Count)
    Write-Host ("  parsed.fundSum  = " + $totalAmount.ToString("F2"))
    Write-Host ("  parsed.funds    = " + ($fundNames -join ","))

    $ok = $true
    if ($categoryCount -lt 1) {
        Write-Host "  [WARN] no categories returned"
        $ok = $false
    }
    if ($fundNames.Count -lt $p.expectCompleteFunds) {
        Write-Host ("  [WARN] expected at least " + $p.expectCompleteFunds + " complete funds, got " + $fundNames.Count)
        $ok = $false
    }
    if ($p.expectTopVisible -and -not $topValue) {
        Write-Host "  [WARN] expected top totalAsset to be visible"
        $ok = $false
    }
    if ($ok) {
        Write-Host "  OK"
        $results.Add(@{ page = $p.name; fileId = $fileId; top = $topValue; source = $topSource; funds = $fundNames.Count; sum = $totalAmount; categories = $categoryCount; pass = $true })
    } else {
        $results.Add(@{ page = $p.name; fileId = $fileId; top = $topValue; source = $topSource; funds = $fundNames.Count; sum = $totalAmount; categories = $categoryCount; pass = $false })
        $failures.Add(@{ page = $p.name; stage = "validation" })
    }
}

Write-Host ""
Write-Host "============================================="
Write-Host "1a.10 单图 minimax E2E 总览（v2.7.1）"
Write-Host "============================================="
foreach ($r in $results) {
    $status = if ($r.pass) { "OK  " } else { "FAIL" }
    Write-Host ("{0}  {1}  top={2} source={3} funds={4} sum={5} cats={6}" -f $status, $r.page, $r.top, $r.source, $r.funds, $r.sum, $r.categories)
}
Write-Host ""
if ($failures.Count -eq 0) {
    Write-Host "RESULT: 4/4 单图 minimax E2E 通过 OK"
} else {
    Write-Host ("RESULT: " + $failures.Count + " 失败 / " + $pages.Count + " 总数")
    foreach ($f in $failures) {
        $stage = $f.stage
        $page = $f.page
        Write-Host ("  - " + $page + "  " + $stage)
    }
}
