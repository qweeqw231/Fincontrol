# Decision 35 / 36: Create desktop shortcuts (launch + stop)
# Pure-ASCII only (PowerShell 5.1 compatibility, no Chinese chars in source)
# Idempotent: existing shortcuts are overwritten with the current target/icon
#
# Decision 36 (2026-09-30): now creates TWO shortcuts, because the single-process
# runtime makes "open" and "close" symmetric one-click actions:
#   FinControl.lnk        -> launch-fincontrol.ps1  (MySQL check + backend, opens 8080)
#   FinControl Stop.lnk   -> stop-fincontrol.ps1    (graceful backend shutdown)

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)

$DesktopPath = [Environment]::GetFolderPath('Desktop')
$logoPath = Join-Path $ProjectRoot 'fincontrol-frontend\public\brand\logo.png'
$launchScript = Join-Path $ProjectRoot 'scripts\desktop\launch-fincontrol.ps1'
$stopScript = Join-Path $ProjectRoot 'scripts\desktop\stop-fincontrol.ps1'
$PowerShellExe = 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe'

if (-not (Test-Path -LiteralPath $logoPath)) {
    Write-Host "[create-shortcut] ERROR: logo.png not found at $logoPath" -ForegroundColor Red
    Write-Host "[create-shortcut] Please run Step 0 first to copy brand assets" -ForegroundColor Yellow
    exit 1
}
foreach ($s in @($launchScript, $stopScript)) {
    if (-not (Test-Path -LiteralPath $s)) {
        Write-Host "[create-shortcut] ERROR: script not found at $s" -ForegroundColor Red
        exit 1
    }
}

# Convert logo.png -> logo.ico if needed (Windows IconLocation requires .ico)
$iconPath = Join-Path (Split-Path -Parent $logoPath) 'logo.ico'
$needsRegen = $true
if (Test-Path -LiteralPath $iconPath) {
    $srcMtime = (Get-Item $logoPath).LastWriteTime
    $icoMtime = (Get-Item $iconPath).LastWriteTime
    if ($srcMtime -le $icoMtime) {
        $needsRegen = $false
    }
}
if ($needsRegen) {
    Write-Host "[create-shortcut] Generating logo.ico from logo.png..." -ForegroundColor Cyan
    try {
        # Inline C# helper: write a PNG-in-ICO file (Vista+ format)
        # PowerShell 5.1 has no ICO image encoder, so we must construct the file manually
        if (-not ('IcoHelper' -as [type])) {
            Add-Type -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;

public static class IcoHelper {
    public static void SavePngAsIco(string pngPath, string icoPath) {
        using (Bitmap bmp = new Bitmap(pngPath)) {
            byte[] pngBytes;
            using (MemoryStream ms = new MemoryStream()) {
                bmp.Save(ms, ImageFormat.Png);
                pngBytes = ms.ToArray();
            }
            byte widthByte  = (byte)(bmp.Width  >= 256 ? 0 : bmp.Width);
            byte heightByte = (byte)(bmp.Height >= 256 ? 0 : bmp.Height);
            using (FileStream fs = new FileStream(icoPath, FileMode.Create)) {
                using (BinaryWriter bw = new BinaryWriter(fs)) {
                    bw.Write((ushort)0);
                    bw.Write((ushort)1);
                    bw.Write((ushort)1);
                    bw.Write(widthByte);
                    bw.Write(heightByte);
                    bw.Write((byte)0);
                    bw.Write((byte)0);
                    bw.Write((ushort)1);
                    bw.Write((ushort)32);
                    bw.Write((uint)pngBytes.Length);
                    bw.Write((uint)(6 + 16));
                    bw.Write(pngBytes);
                }
            }
        }
    }
}
'@ -ReferencedAssemblies System.Drawing
        }
        [IcoHelper]::SavePngAsIco($logoPath, $iconPath)
        Write-Host "[create-shortcut] OK logo.ico generated at $iconPath" -ForegroundColor Green
    } catch {
        Write-Host "[create-shortcut] WARN: failed to generate logo.ico, fallback to logo.png" -ForegroundColor Yellow
        Write-Host "[create-shortcut]   $($_.Exception.Message)" -ForegroundColor Gray
        $iconPath = $logoPath
    }
} else {
    Write-Host "[create-shortcut] logo.ico is up to date, reusing existing file" -ForegroundColor Gray
}

Write-Host "[create-shortcut] Creating/overwriting desktop shortcuts..." -ForegroundColor Cyan
Write-Host "[create-shortcut]   Desktop path: $DesktopPath" -ForegroundColor Gray
Write-Host "[create-shortcut]   Icon: $iconPath" -ForegroundColor Gray

$targets = @(
    @{
        Lnk         = Join-Path $DesktopPath 'FinControl.lnk'
        Script      = $launchScript
        Description = 'FinControl - launch (MySQL check + backend; opens http://localhost:8080/)'
    },
    @{
        Lnk         = Join-Path $DesktopPath 'FinControl Stop.lnk'
        Script      = $stopScript
        Description = 'FinControl - stop (graceful backend shutdown; MySQL80 is left running)'
    }
)

try {
    $WshShell = New-Object -ComObject WScript.Shell
    foreach ($t in $targets) {
        $Shortcut = $WshShell.CreateShortcut($t.Lnk)
        $Shortcut.TargetPath = $PowerShellExe
        $Shortcut.Arguments = "-ExecutionPolicy Bypass -File `"$($t.Script)`""
        $Shortcut.WorkingDirectory = $ProjectRoot
        $Shortcut.IconLocation = $iconPath
        $Shortcut.Description = $t.Description
        $Shortcut.WindowStyle = 7
        $Shortcut.Save()
        Write-Host "[create-shortcut] OK $(Split-Path -Leaf $t.Lnk)" -ForegroundColor Green
        Write-Host "[create-shortcut]    -> $($t.Script)" -ForegroundColor Gray
    }

    Write-Host ""
    Write-Host "You can now:" -ForegroundColor Cyan
    Write-Host "  1. Double-click 'FinControl.lnk' to launch (browser opens http://localhost:8080/)" -ForegroundColor White
    Write-Host "  2. Use the page (frontend + backend share ONE port 8080)" -ForegroundColor White
    Write-Host "  3. Click the top-right shutdown button, or double-click 'FinControl Stop.lnk'" -ForegroundColor White
    Write-Host ""
    Write-Host "Notes:" -ForegroundColor Cyan
    Write-Host "  - MySQL80 is a Windows auto-start service; neither shortcut starts/stops it" -ForegroundColor Gray
    Write-Host "    (toggling a service needs admin rights and would raise a UAC prompt every time)" -ForegroundColor Gray
    Write-Host "  - Frontend code changed? Rebuild once, then relaunch:" -ForegroundColor Gray
    Write-Host "      cd fincontrol-frontend; npm run build" -ForegroundColor Gray
    Write-Host "    (or run launch-fincontrol.ps1 -ForceRebuild)" -ForegroundColor Gray
    Write-Host "  - Frontend dev mode still works: cd fincontrol-frontend; npm run dev" -ForegroundColor Gray
    Write-Host ""
} catch {
    Write-Host "[create-shortcut] ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
