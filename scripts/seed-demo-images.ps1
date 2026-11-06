# 데모 원고(seed/demo/content/*.json, seed/demo/groupbuys.json)의 이미지 칸을 자리표시 그림(JPEG)으로 그린다 — Windows PowerShell 5.1 + System.Drawing.
# 결과: seed/demo/images/<글 key>-<번호>.jpg · 광고 배너 ad-<key>.png(seed/demo/ads.json) (이미 있으면 건너뛴다, -Force 면 다시 그린다). 로더(scripts/seed-demo.mjs)는 이 파일만 읽으므로
# 그림을 저장소에 두면 다른 OS(AWS dev)에서는 이 스크립트 없이 로더만 돌린다. 문서: docs/seed-demo.md
param([switch]$Force)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$demo = Join-Path $root 'seed\demo'
$out = Join-Path $demo 'images'
New-Item -ItemType Directory -Force $out | Out-Null

$fontName = 'Malgun Gothic'
$palettes = @(
    @('#1e3a8a', '#3b82f6'), @('#065f46', '#34d399'), @('#7c2d12', '#fb923c'), @('#581c87', '#c084fc'),
    @('#0f172a', '#64748b'), @('#831843', '#f472b6'), @('#134e4a', '#2dd4bf'), @('#713f12', '#facc15'),
    @('#1e1b4b', '#818cf8'), @('#7f1d1d', '#f87171'), @('#164e63', '#22d3ee'), @('#3f6212', '#a3e635')
)

function Hash([string]$s) { $h = 0; foreach ($c in $s.ToCharArray()) { $h = ($h * 31 + [int]$c) % 2147483647 }; return $h }
function Col([string]$hex, [int]$a = 255) { $c = [System.Drawing.ColorTranslator]::FromHtml($hex); return [System.Drawing.Color]::FromArgb($a, $c.R, $c.G, $c.B) }

function DrawText($g, [string]$text, [float]$size, $rect, $color, [string]$align = 'Center', [bool]$bold = $true, [bool]$outline = $false) {
    $style = if ($bold) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }
    $font = [System.Drawing.Font]::new($fontName, $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::$align
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    if ($outline) {
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        $path.AddString($text, $font.FontFamily, [int]$style, $size, $rect, $fmt)
        $pen = [System.Drawing.Pen]::new([System.Drawing.Color]::Black, [Math]::Max(4, $size / 9))
        $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
        $g.DrawPath($pen, $path)
        $g.FillPath(([System.Drawing.SolidBrush]::new($color)), $path)
    } else {
        $g.DrawString($text, $font, ([System.Drawing.SolidBrush]::new($color)), $rect, $fmt)
    }
}

function Render([string]$kind, [string]$caption, [string]$scene, [string]$file) {
    $seed = Hash ($caption + $scene + $file)
    $pal = $palettes[$seed % $palettes.Count]
    $rnd = [System.Random]::new($seed)
    $imgW = 960; $imgH = 720
    if ($kind -eq 'meme') { $imgW = 800; $imgH = 800 }
    $bmp = [System.Drawing.Bitmap]::new($imgW, $imgH)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
    $full = [System.Drawing.Rectangle]::new(0, 0, $imgW, $imgH)
    $white = [System.Drawing.Color]::White
    switch ($kind) {
        'meme' {
            $g.FillRectangle(([System.Drawing.Drawing2D.LinearGradientBrush]::new($full, (Col $pal[1]), (Col $pal[0]), 60)), $full)
            for ($i = 0; $i -lt 6; $i++) {
                $r = $rnd.Next(80, 260); $g.FillEllipse(([System.Drawing.SolidBrush]::new((Col '#ffffff' 40))), $rnd.Next(-60, $imgW), $rnd.Next(120, $imgH - 200), $r, $r)
            }
            # 가운데 얼굴(단순한 이모지 모양)
            $cx = $imgW / 2; $cy = $imgH / 2
            $g.FillEllipse(([System.Drawing.SolidBrush]::new((Col '#fde047'))), $cx - 150, $cy - 150, 300, 300)
            $g.FillEllipse(([System.Drawing.SolidBrush]::new((Col '#1f2937'))), $cx - 80, $cy - 60, 40, 55)
            $g.FillEllipse(([System.Drawing.SolidBrush]::new((Col '#1f2937'))), $cx + 40, $cy - 60, 40, 55)
            $pen = [System.Drawing.Pen]::new((Col '#1f2937'), 14); $pen.StartCap = 'Round'; $pen.EndCap = 'Round'
            $mouth = $rnd.Next(0, 3)
            if ($mouth -eq 0) { $g.DrawArc($pen, $cx - 80, $cy - 20, 160, 110, 20, 140) } elseif ($mouth -eq 1) { $g.DrawArc($pen, $cx - 70, $cy + 50, 140, 90, 200, 140) } else { $g.DrawEllipse($pen, $cx - 30, $cy + 40, 60, 60) }
            $parts = $caption -split '\s*/\s*', 2
            $top = $parts[0]; $bottom = if ($parts.Count -gt 1) { $parts[1] } else { '' }
            DrawText $g $top 64 ([System.Drawing.RectangleF]::new(20, 20, $imgW - 40, 170)) $white 'Center' $true $true
            if ($bottom) { DrawText $g $bottom 64 ([System.Drawing.RectangleF]::new(20, $imgH - 190, $imgW - 40, 170)) $white 'Center' $true $true }
        }
        'screenshot' {
            $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col '#e5e7eb'))), $full)
            $win = [System.Drawing.Rectangle]::new(40, 40, $imgW - 80, $imgH - 80)
            $g.FillRectangle(([System.Drawing.SolidBrush]::new($white)), $win)
            $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col $pal[0]))), 40, 40, $imgW - 80, 56)
            foreach ($k in 0..2) { $g.FillEllipse(([System.Drawing.SolidBrush]::new((Col @('#f87171', '#fbbf24', '#34d399')[$k]))), 60 + $k * 28, 58, 18, 18) }
            DrawText $g $caption 26 ([System.Drawing.RectangleF]::new(160, 40, $imgW - 280, 56)) $white 'Near'
            $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col '#f1f5f9'))), 40, 96, 200, $imgH - 136)
            for ($i = 0; $i -lt 8; $i++) { $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col '#cbd5e1'))), 60, 126 + $i * 48, $rnd.Next(90, 160), 14) }
            $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col $pal[1] 60))), 270, 126, $imgW - 350, 150)
            DrawText $g $scene 24 ([System.Drawing.RectangleF]::new(290, 136, $imgW - 390, 130)) (Col '#0f172a') 'Near' $false
            for ($i = 0; $i -lt 6; $i++) { $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col '#e2e8f0'))), 270, 310 + $i * 46, $rnd.Next(300, $imgW - 360), 16) }
            $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col $pal[0]))), $imgW - 230, $imgH - 110, 150, 44)
        }
        'product' {
            $g.FillRectangle(([System.Drawing.Drawing2D.LinearGradientBrush]::new($full, (Col '#ffffff'), (Col '#e2e8f0'), 90)), $full)
            $g.FillEllipse(([System.Drawing.SolidBrush]::new((Col '#000000' 30))), 230, 560, 500, 50)
            $box = [System.Drawing.Rectangle]::new(270, 140, 420, 420)
            $g.FillRectangle(([System.Drawing.Drawing2D.LinearGradientBrush]::new($box, (Col $pal[1]), (Col $pal[0]), 45)), $box)
            $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col '#ffffff' 70))), 300, 170, 360, 60)
            $g.FillEllipse(([System.Drawing.SolidBrush]::new((Col '#ffffff' 90))), 410, 300, 140, 140)
            DrawText $g $caption 40 ([System.Drawing.RectangleF]::new(40, 600, $imgW - 80, 90)) (Col '#0f172a')
        }
        'chart' {
            $g.FillRectangle(([System.Drawing.SolidBrush]::new($white)), $full)
            DrawText $g $caption 38 ([System.Drawing.RectangleF]::new(40, 20, $imgW - 80, 80)) (Col '#0f172a') 'Near'
            $pen = [System.Drawing.Pen]::new((Col '#94a3b8'), 2)
            $g.DrawLine($pen, 100, 640, $imgW - 60, 640); $g.DrawLine($pen, 100, 120, 100, 640)
            for ($i = 1; $i -le 4; $i++) { $g.DrawLine(([System.Drawing.Pen]::new((Col '#e2e8f0'), 1)), 100, 640 - $i * 120, $imgW - 60, 640 - $i * 120) }
            $n = 10; $bw = ($imgW - 200) / $n; $prev = $null; $v = 300
            for ($i = 0; $i -lt $n; $i++) {
                $v = [Math]::Max(60, [Math]::Min(480, $v + $rnd.Next(-90, 110)))
                $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col $pal[1] 150))), 120 + $i * $bw, 640 - $v, $bw - 16, $v)
                $pt = [System.Drawing.PointF]::new((120 + $i * $bw + ($bw - 16) / 2), (640 - $v - 20))
                if ($prev) { $g.DrawLine(([System.Drawing.Pen]::new((Col $pal[0]), 5)), $prev, $pt) }
                $prev = $pt
            }
        }
        default {
            # photo · illustration
            $g.FillRectangle(([System.Drawing.Drawing2D.LinearGradientBrush]::new($full, (Col $pal[0]), (Col $pal[1]), $rnd.Next(20, 160))), $full)
            for ($i = 0; $i -lt 14; $i++) {
                $r = $rnd.Next(40, 300)
                $alpha = if ($kind -eq 'illustration') { 110 } else { 45 }
                $c = if ($i % 3 -eq 0) { '#ffffff' } elseif ($i % 3 -eq 1) { $pal[0] } else { $pal[1] }
                if ($kind -eq 'illustration' -and $i % 2 -eq 0) {
                    $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col $c $alpha))), $rnd.Next(-50, $imgW), $rnd.Next(-50, $imgH), $r, $r)
                } else {
                    $g.FillEllipse(([System.Drawing.SolidBrush]::new((Col $c $alpha))), $rnd.Next(-100, $imgW), $rnd.Next(-100, $imgH), $r, $r)
                }
            }
            if ($kind -ne 'illustration') {
                # 지평선 느낌의 아래 띠
                $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col '#000000' 50))), 0, $imgH * 0.68, $imgW, $imgH * 0.32)
            }
            $g.FillRectangle(([System.Drawing.SolidBrush]::new((Col '#000000' 120))), 0, $imgH - 150, $imgW, 150)
            DrawText $g $caption 44 ([System.Drawing.RectangleF]::new(40, $imgH - 150, $imgW - 80, 90)) $white 'Near'
            DrawText $g $scene 22 ([System.Drawing.RectangleF]::new(40, $imgH - 70, $imgW - 80, 50)) (Col '#e2e8f0') 'Near' $false
        }
    }
    $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
    $params = [System.Drawing.Imaging.EncoderParameters]::new(1)
    $params.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality, [long]78)
    $bmp.Save($file, $codec, $params)
    $g.Dispose(); $bmp.Dispose()
}

function Specs($post) {
    $list = @()
    if ($post.images) { $list += $post.images }
    if ($post.image) { $list += $post.image }
    return $list
}

$count = 0; $skipped = 0
$posts = @()
foreach ($f in Get-ChildItem (Join-Path $demo 'content') -Filter *.json) {
    $doc = Get-Content $f.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($doc.posts) { $posts += $doc.posts }
    if ($doc.deals) { $posts += $doc.deals }
    if ($doc.groups) { foreach ($gr in $doc.groups) { $posts += $gr.posts } }
}
$gbFile = Join-Path $demo 'groupbuys.json'
if (Test-Path $gbFile) { $posts += (Get-Content $gbFile -Raw -Encoding UTF8 | ConvertFrom-Json).products }

foreach ($p in $posts) {
    $n = 0
    foreach ($img in (Specs $p)) {
        $n++
        $file = Join-Path $out ("{0}-{1}.jpg" -f $p.key, $n)
        if ((Test-Path $file) -and -not $Force) { $skipped++; continue }
        $kind = if ($img.kind) { $img.kind } else { 'photo' }
        Render $kind ([string]$img.caption) ([string]$img.scene) $file
        $count++
    }
}
# 광고 배너(seed/demo/ads.json) — 규격 비율 그대로(띠 1200×100 · 네모 600×500 = 6:5 · 날개 320×1200 = 4:15), PNG. 광고 자리는 규격 틀에 맞춰 보이므로 비율이 ±3% 안이어야 한다
function RenderBanner($ad, [string]$file) {
    $b = $ad.banner
    if ($ad.type -eq 'STRIP_BANNER') { $imgW = 1200; $imgH = 100 } elseif ($ad.type -eq 'WING_BANNER') { $imgW = 320; $imgH = 1200 } else { $imgW = 600; $imgH = 500 }
    $bmp = [System.Drawing.Bitmap]::new($imgW, $imgH)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
    $full = [System.Drawing.Rectangle]::new(0, 0, $imgW, $imgH)
    $g.FillRectangle([System.Drawing.Drawing2D.LinearGradientBrush]::new($full, (Col $b.palette[0]), (Col $b.palette[1]), 20), $full)
    $white = [System.Drawing.Color]::White
    $rnd = [System.Random]::new((Hash $ad.key))
    for ($i = 0; $i -lt 6; $i++) { $r = $rnd.Next([int]($imgH * 0.6), [int]($imgH * 2)); $g.FillEllipse([System.Drawing.SolidBrush]::new((Col '#ffffff' 28)), $rnd.Next(0, $imgW), $rnd.Next(-$r, $imgH), $r, $r) }
    if ($ad.type -eq 'STRIP_BANNER') {
        DrawText $g $b.brand 20 ([System.Drawing.RectangleF]::new(36, 0, 280, $imgH)) (Col '#ffffff' 220) 'Near' $true
        DrawText $g $b.headline 27 ([System.Drawing.RectangleF]::new(290, 0, $imgW - 530, $imgH)) $white 'Near' $true
        $g.FillRectangle([System.Drawing.SolidBrush]::new($white), $imgW - 220, 26, 180, 48)
        DrawText $g $b.cta 20 ([System.Drawing.RectangleF]::new($imgW - 220, 26, 180, 48)) (Col $b.palette[0]) 'Center' $true
    } elseif ($ad.type -eq 'WING_BANNER') {
        DrawText $g $b.brand 26 ([System.Drawing.RectangleF]::new(24, 60, $imgW - 48, 80)) (Col '#ffffff' 220) 'Near' $true
        DrawText $g $b.headline 34 ([System.Drawing.RectangleF]::new(24, 200, $imgW - 48, 380)) $white 'Near' $true
        DrawText $g $b.sub 24 ([System.Drawing.RectangleF]::new(24, 620, $imgW - 48, 100)) (Col '#ffffff' 230) 'Near' $false
        $g.FillRectangle([System.Drawing.SolidBrush]::new($white), 24, 1040, $imgW - 48, 84)
        DrawText $g $b.cta 28 ([System.Drawing.RectangleF]::new(24, 1040, $imgW - 48, 84)) (Col $b.palette[0]) 'Center' $true
    } else {
        DrawText $g $b.brand 26 ([System.Drawing.RectangleF]::new(40, 36, $imgW - 80, 40)) (Col '#ffffff' 220) 'Near' $true
        DrawText $g $b.headline 52 ([System.Drawing.RectangleF]::new(40, 110, $imgW - 80, 150)) $white 'Near' $true
        DrawText $g $b.sub 26 ([System.Drawing.RectangleF]::new(40, 270, $imgW - 80, 50)) (Col '#ffffff' 230) 'Near' $false
        $g.FillRectangle([System.Drawing.SolidBrush]::new($white), 40, 370, 260, 76)
        DrawText $g $b.cta 28 ([System.Drawing.RectangleF]::new(40, 370, 260, 76)) (Col $b.palette[0]) 'Center' $true
    }
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

$adsFile = Join-Path $demo 'ads.json'
if (Test-Path $adsFile) {
    foreach ($ad in (Get-Content $adsFile -Raw -Encoding UTF8 | ConvertFrom-Json).ads) {
        if (-not $ad.banner) { continue }
        $file = Join-Path $out ("ad-{0}.png" -f $ad.key)
        if ((Test-Path $file) -and -not $Force) { $skipped++; continue }
        RenderBanner $ad $file
        $count++
    }
}

Write-Output "그림 $count 장 그림, $skipped 장은 이미 있어 건너뜀 → $out"
