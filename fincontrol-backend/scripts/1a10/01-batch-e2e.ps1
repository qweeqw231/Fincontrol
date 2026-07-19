# 1a.10 真实 E2E（4 张图 + 批量解析）。调用方：PowerShell。
# 启动后端：Start-Process -FilePath 'C:\Program Files\Java\jdk-17\bin\java.exe' -ArgumentList '-jar','target\fincontrol-backend.jar'

$ErrorActionPreference = "Stop"
$h = "http://localhost:8080"
$u = 18008
$base = "c:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend"
$samples = Join-Path $base "uploads\samples"
$pages = @(
    "phase1a2-alipay-fund-list-20260715-2355-1.jpg",
    "phase1a2-alipay-fund-list-20260715-2355-2.jpg",
    "phase1a2-alipay-fund-list-20260715-2355-3.jpg",
    "phase1a2-alipay-fund-list-20260715-2356-1.jpg"
)
$reqPath = Join-Path $base "logs\1a10-batch-body.json"

$fileIds = New-Object System.Collections.Generic.List[string]
foreach ($p in $pages) {
    $path = Join-Path $samples $p
    Write-Host "--- upload $p"
    $r = & curl.exe -s -X POST -H "X-User-Id: 1" -F "file=@$path" ($h + "/api/screenshot/upload")
    $obj = $r | ConvertFrom-Json
    if ($obj.data -and $obj.data.fileId) {
        $fileIds.Add($obj.data.fileId)
        Write-Host ("OK  -> " + $obj.data.fileId)
    } else {
        Write-Host ("FAIL -> " + $r)
    }
}
Write-Host ("fileIds=" + ($fileIds -join ","))
if ($fileIds.Count -ne 4) { throw "upload count != 4" }

# 用 UTF-8 no-BOM 写请求体（PowerShell 默认带 BOM 会让 Jackson 报错）
$body = (@{ userId = $u; fileIds = $fileIds } | ConvertTo-Json -Compress)
[IO.File]::WriteAllText($reqPath, $body, (New-Object System.Text.UTF8Encoding $false))
Write-Host ("reqBody bytes=" + (Get-Item -LiteralPath $reqPath).Length + ' @ ' + $reqPath)

Write-Host "=== batch ==="
$batch = & curl.exe -s --data-binary ('@' + $reqPath) -H "X-User-Id: 1" -H "Content-Type: application/json" ($h + "/api/screenshot/parse-batch")
Write-Host $batch
