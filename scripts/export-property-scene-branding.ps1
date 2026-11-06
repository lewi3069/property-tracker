# Export the generated property-scene master into Android launcher resources.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $projectRoot 'assets/branding'
$res = Join-Path $projectRoot 'app/src/main/res'
$source = [Drawing.Bitmap]::FromFile((Join-Path $assets 'property-tracker-option-b-house-camera.png'))
if ($source.Width -ne $source.Height) { $source.Dispose(); throw 'A square icon master is required' }

function Export-Art([string]$path, [int]$width, [int]$height, [bool]$banner) {
    $bitmap = [Drawing.Bitmap]::new($width, $height)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([Drawing.ColorTranslator]::FromHtml('#101516'))
    $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    if ($banner) {
        $graphics.ScaleTransform($width / 320.0, $height / 180.0)
        $graphics.DrawImage($source, [Drawing.RectangleF]::new(6, 7, 164, 164))
        $font = [Drawing.Font]::new('Segoe UI Semibold', 25, [Drawing.FontStyle]::Regular, [Drawing.GraphicsUnit]::Pixel)
        $ivory = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#F3F7EE'))
        $sage = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#CBE7AB'))
        $graphics.DrawString('Property', $font, $ivory, [Drawing.PointF]::new(174, 55), [Drawing.StringFormat]::GenericTypographic)
        $graphics.DrawString('Tracker', $font, $sage, [Drawing.PointF]::new(174, 92), [Drawing.StringFormat]::GenericTypographic)
        $font.Dispose(); $ivory.Dispose(); $sage.Dispose()
    } else {
        $graphics.DrawImage($source, [Drawing.Rectangle]::new(0, 0, $width, $height))
    }
    try { $bitmap.Save($path, [Drawing.Imaging.ImageFormat]::Png) }
    finally { $graphics.Dispose(); $bitmap.Dispose() }
}

try {
    Export-Art (Join-Path $assets 'property-tracker-property-scene-preview.png') 512 512 $false
    Export-Art (Join-Path $assets 'property-tracker-property-scene-banner.png') 1280 720 $true
    Export-Art (Join-Path $assets 'property-tracker-icon.png') 512 512 $false
    Export-Art (Join-Path $assets 'property-tracker-tv-banner.png') 1280 720 $true
    foreach ($density in @(@('mdpi',80,160,90), @('hdpi',120,240,135), @('xhdpi',160,320,180), @('xxhdpi',240,480,270), @('xxxhdpi',320,640,360))) {
        $mipmap = Join-Path $res "mipmap-$($density[0])"
        $drawable = Join-Path $res "drawable-$($density[0])"
        $tvMipmap = Join-Path $res "mipmap-television-$($density[0])"
        New-Item -ItemType Directory -Force $mipmap, $drawable, $tvMipmap | Out-Null
        Export-Art (Join-Path $mipmap 'ic_property_tracker.png') $density[1] $density[1] $false
        Export-Art (Join-Path $drawable 'tv_banner.png') $density[2] $density[3] $true
        # Fire TV can use android:icon instead of android:banner for sideloaded tiles.
        Export-Art (Join-Path $tvMipmap 'ic_property_tracker.png') $density[2] $density[3] $true
    }
} finally { $source.Dispose() }
Write-Output 'Exported option B: square fallback icons, rectangular television icons, and TV banners.'
