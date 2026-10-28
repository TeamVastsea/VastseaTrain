# Convert the artist's rail OBJ into Minecraft block-model space.
#
# Why a script instead of the model JSON "transform" field:
#   Forge's OBJ loader composes the blockstate transform, the model root transform and
#   Transformation.blockCenterToCorner(), and the JSON transform origin defaults to the
#   "opposing corner". Getting a centred, ground-aligned result through that chain is
#   fragile and hard to verify without launching the game. Baking the conversion into a
#   derived OBJ is deterministic and can be checked by inspecting numbers.
#
# Mapping (OBJ units -> block space, 1 block = 1.0):
#   scale  = TARGET_LENGTH / modelLengthZ      (so one segment spans TARGET_LENGTH blocks)
#   x'     = scale * x + 0.5                   (centre the model inside the block)
#   z'     = scale * z + 0.5
#   y'     = scale * (y - GROUND_Y)            (ballast top sits exactly on block bottom)
#
# Only vertex lines are rewritten; normals are unchanged by a uniform positive scale,
# and UVs are untouched.

param(
    [string]$Source = "C:\Users\MCTV\Documents\b1\b1.obj",
    [string]$Target = "src\main\resources\assets\vastseatrain\models\block\rail\rail.obj",
    [double]$TargetLength = 2.0,
    [double]$GroundY = 0.285666
)

$ErrorActionPreference = 'Stop'

$lines = [IO.File]::ReadAllLines($Source)
$xs = New-Object 'System.Collections.Generic.List[double]'
$ys = New-Object 'System.Collections.Generic.List[double]'
$zs = New-Object 'System.Collections.Generic.List[double]'

foreach ($line in $lines) {
    if ($line -match '^v\s+(\S+)\s+(\S+)\s+(\S+)') {
        $xs.Add([double]$Matches[1]); $ys.Add([double]$Matches[2]); $zs.Add([double]$Matches[3])
    }
}

$lenZ = ($zs | Measure-Object -Maximum).Maximum - ($zs | Measure-Object -Minimum).Minimum
$scale = $TargetLength / $lenZ
$inv = [System.Globalization.CultureInfo]::InvariantCulture

$out = New-Object 'System.Collections.Generic.List[string]'
foreach ($line in $lines) {
    if ($line -match '^v\s+(\S+)\s+(\S+)\s+(\S+)(.*)$') {
        $x = [double]$Matches[1]; $y = [double]$Matches[2]; $z = [double]$Matches[3]; $rest = $Matches[4]
        $nx = $scale * $x + 0.5
        $ny = $scale * ($y - $GroundY)
        $nz = $scale * $z + 0.5
        $out.Add(('v {0} {1} {2}{3}' -f $nx.ToString('0.######', $inv), $ny.ToString('0.######', $inv), $nz.ToString('0.######', $inv), $rest))
    }
    elseif ($line -match '^mtllib\s+') {
        $out.Add('mtllib rail.mtl')
    }
    else {
        $out.Add($line)
    }
}

$dir = Split-Path -Parent $Target
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
[IO.File]::WriteAllLines($Target, $out, (New-Object System.Text.UTF8Encoding $false))

$sizeX = (($xs | Measure-Object -Maximum).Maximum - ($xs | Measure-Object -Minimum).Minimum) * $scale
$sizeY = (($ys | Measure-Object -Maximum).Maximum - ($ys | Measure-Object -Minimum).Minimum) * $scale
"source      : $Source"
"target      : $Target"
"model length: {0:N4} (Z)" -f $lenZ
"scale       : {0:N6}" -f $scale
"result size : {0:N3} x {1:N3} x {2:N3} blocks (X x Y x Z)" -f $sizeX, $sizeY, $TargetLength
"ground Y    : {0} -> block bottom 0" -f $GroundY
