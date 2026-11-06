# Rebuild the native launcher artwork and shareable previews on Windows.
# Uses Windows' Segoe UI Semibold for the outlined wordmark; no font is bundled.
# TV sizing: https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines
param([switch]$LegacyArtwork)
$ErrorActionPreference = 'Stop'
if (!$LegacyArtwork -and (Test-Path -LiteralPath (Join-Path $PSScriptRoot '../assets/branding/property-tracker-property-scene.png'))) {
    & (Join-Path $PSScriptRoot 'export-property-scene-branding.ps1')
    return
}
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $root 'assets/branding'
$res = Join-Path $root 'app/src/main/res'
New-Item -ItemType Directory -Force $assets, "$res/mipmap-mdpi", "$res/mipmap-hdpi", "$res/mipmap-xhdpi", "$res/mipmap-xxhdpi", "$res/mipmap-xxxhdpi", "$res/drawable-mdpi", "$res/drawable-hdpi", "$res/drawable-xhdpi", "$res/drawable-xxhdpi", "$res/drawable-xxxhdpi" | Out-Null
$culture = [Globalization.CultureInfo]::InvariantCulture
$background = '#101516'
$sage = '#CBE7AB'
$mint = '#78B99C'

function Number([single]$value) { $value.ToString('0.###', $culture) }
function Path-Data([Drawing.Drawing2D.GraphicsPath]$path) {
    $points = $path.PathPoints; $types = $path.PathTypes
    $parts = [Collections.Generic.List[string]]::new()
    for ($i = 0; $i -lt $points.Length; $i++) {
        $type = $types[$i] -band 7
        if ($type -eq 0) { $parts.Add("M$(Number $points[$i].X),$(Number $points[$i].Y)") }
        elseif ($type -eq 1) { $parts.Add("L$(Number $points[$i].X),$(Number $points[$i].Y)") }
        elseif ($type -eq 3) {
            $parts.Add("C$(Number $points[$i].X),$(Number $points[$i].Y) $(Number $points[$i+1].X),$(Number $points[$i+1].Y) $(Number $points[$i+2].X),$(Number $points[$i+2].Y)")
            $i += 2
        } else { throw 'Unsupported path segment' }
        if ($types[$i] -band 128) { $parts.Add('Z') }
    }
    $parts -join ' '
}
function Layer($path, $fill, $stroke, $width) {
    [pscustomobject]@{ Path=$path; Fill=$fill; Stroke=$stroke; Width=$width }
}
function Circle([single]$x, [single]$y, [single]$radius) {
    $path = [Drawing.Drawing2D.GraphicsPath]::new()
    $path.AddEllipse($x-$radius, $y-$radius, $radius*2, $radius*2)
    $path
}
$house = [Drawing.Drawing2D.GraphicsPath]::new()
$house.AddLines([Drawing.PointF[]]@([Drawing.PointF]::new(30,48), [Drawing.PointF]::new(54,28), [Drawing.PointF]::new(78,48), [Drawing.PointF]::new(78,71)))
$house.AddBezier(78,71, 78,74, 76,76, 73,76)
$house.AddLine(73,76,35,76)
$house.AddBezier(35,76,32,76,30,74,30,71)
$house.CloseFigure()
$camera = [Drawing.Drawing2D.GraphicsPath]::new()
$camera.AddBezier(37,48,37,46.3,38.3,45,40,45)
$camera.AddLines([Drawing.PointF[]]@([Drawing.PointF]::new(40,45), [Drawing.PointF]::new(45,45), [Drawing.PointF]::new(48,41), [Drawing.PointF]::new(60,41), [Drawing.PointF]::new(63,45), [Drawing.PointF]::new(68,45)))
$camera.AddBezier(68,45,69.7,45,71,46.3,71,48)
$camera.AddLine(71,48,71,64)
$camera.AddBezier(71,64,71,65.7,69.7,67,68,67)
$camera.AddLine(68,67,40,67)
$camera.AddBezier(40,67,38.3,67,37,65.7,37,64)
$camera.CloseFigure()
$cameraOffset = [Drawing.Drawing2D.Matrix]::new(); $cameraOffset.Translate(0,3)
$camera.Transform($cameraOffset); $cameraOffset.Dispose()
$mark = @((Layer $house $null $sage 4.6), (Layer $camera $null $mint 2.7), (Layer (Circle 54 58.5 6.4) $null $sage 2.9))

$font = [Drawing.FontFamily]::new('Segoe UI Semibold')
if ($font.Name -ne 'Segoe UI Semibold') { throw 'Segoe UI Semibold is required to regenerate the wordmark' }
function Word([string]$text, [single]$x, [single]$y, [string]$color) {
    $path = [Drawing.Drawing2D.GraphicsPath]::new([Drawing.Drawing2D.FillMode]::Winding)
    $path.AddString($text, $font, [int][Drawing.FontStyle]::Regular, 29, [Drawing.PointF]::new(0,0), [Drawing.StringFormat]::GenericTypographic)
    $bounds = $path.GetBounds()
    $matrix = [Drawing.Drawing2D.Matrix]::new(); $matrix.Translate($x-$bounds.X, $y-$bounds.Y)
    $path.Transform($matrix); $matrix.Dispose()
    Layer $path $color $null 0
}
$words = @((Word 'Property' 132 60 '#F3F7EE'), (Word 'Tracker' 132 96 $sage))
function Svg-Layers($layers) {
    foreach ($layer in $layers) {
        $fill = if ($layer.Fill) { $layer.Fill } else { 'none' }
        $stroke = if ($layer.Stroke) { " stroke=`"$($layer.Stroke)`" stroke-width=`"$(Number $layer.Width)`" stroke-linecap=`"round`" stroke-linejoin=`"round`"" } else { '' }
        "<path fill=`"$fill`"$stroke d=`"$(Path-Data $layer.Path)`"/>"
    }
}
function Save([string]$path, [string]$content) { [IO.File]::WriteAllText($path, $content + "`n", [Text.UTF8Encoding]::new($false)) }
$markSvg = (Svg-Layers $mark) -join "`n"
$wordSvg = (Svg-Layers $words) -join "`n"
Save "$assets/property-tracker-icon.svg" "<svg xmlns=`"http://www.w3.org/2000/svg`" width=`"512`" height=`"512`" viewBox=`"0 0 108 108`"><rect width=`"108`" height=`"108`" fill=`"$background`"/><g transform=`"translate(-13.5 -13.5) scale(1.25)`">$markSvg</g></svg>"
Save "$assets/property-tracker-tv-banner.svg" "<svg xmlns=`"http://www.w3.org/2000/svg`" width=`"1280`" height=`"720`" viewBox=`"0 0 320 180`"><rect width=`"320`" height=`"180`" fill=`"$background`"/><g transform=`"translate(-10 9) scale(1.5)`">$markSvg</g>$wordSvg</svg>"

function Draw-Layers($graphics, $layers) {
    foreach ($layer in $layers) {
        if ($layer.Fill) {
            $brush = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($layer.Fill))
            $graphics.FillPath($brush, $layer.Path); $brush.Dispose()
        }
        if ($layer.Stroke) {
            $pen = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml($layer.Stroke), [single]$layer.Width)
            $pen.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
            $pen.StartCap = $pen.EndCap = [Drawing.Drawing2D.LineCap]::Round
            $graphics.DrawPath($pen, $layer.Path); $pen.Dispose()
        }
    }
}
function Preview([string]$name, [int]$width, [int]$height, [bool]$banner, [string]$directory=$assets) {
    $bitmap = [Drawing.Bitmap]::new($width,$height)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([Drawing.ColorTranslator]::FromHtml($background))
    if ($banner) { $graphics.ScaleTransform($width/320.0,$height/180.0) } else { $graphics.ScaleTransform($width/108.0,$height/108.0) }
    $state = $graphics.Save()
    if ($banner) { $graphics.TranslateTransform(-10,9); $graphics.ScaleTransform(1.5,1.5) }
    else { $graphics.TranslateTransform(-13.5,-13.5); $graphics.ScaleTransform(1.25,1.25) }
    Draw-Layers $graphics $mark
    $graphics.Restore($state)
    if ($banner) { Draw-Layers $graphics $words }
    $bitmap.Save("$directory/$name", [Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $bitmap.Dispose()
}
Preview 'property-tracker-icon.png' 512 512 $false
Preview 'property-tracker-tv-banner.png' 1280 720 $true
Preview 'ic_property_tracker.png' 80 80 $false "$res/mipmap-mdpi"
Preview 'ic_property_tracker.png' 120 120 $false "$res/mipmap-hdpi"
Preview 'ic_property_tracker.png' 160 160 $false "$res/mipmap-xhdpi"
Preview 'ic_property_tracker.png' 240 240 $false "$res/mipmap-xxhdpi"
Preview 'ic_property_tracker.png' 320 320 $false "$res/mipmap-xxxhdpi"
Preview 'tv_banner.png' 160 90 $true "$res/drawable-mdpi"
Preview 'tv_banner.png' 240 135 $true "$res/drawable-hdpi"
Preview 'tv_banner.png' 320 180 $true "$res/drawable-xhdpi"
Preview 'tv_banner.png' 480 270 $true "$res/drawable-xxhdpi"
Preview 'tv_banner.png' 640 360 $true "$res/drawable-xxxhdpi"
foreach ($layer in @($mark) + @($words)) { $layer.Path.Dispose() }
$font.Dispose()
Write-Output 'Generated PNG launcher icons, density-specific TV banners, SVG masters, and PNG previews.'
