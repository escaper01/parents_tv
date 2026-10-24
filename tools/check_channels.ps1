# Plays each channel on a device running Parents TV (through its web API) and reports whether the
# picture and sound are really advancing, using the app's /api/status frame counters.
# The app must be in front on the device.
#   powershell -ExecutionPolicy Bypass -File tools\check_channels.ps1 -Device 192.168.1.1:5555 -Group Morocco
param(
    [string]$Device = '192.168.1.1:5555',
    [string]$Group = '',
    [string]$Pin = '5048',
    [int]$WaitSeconds = 15,
    [int]$SampleSeconds = 4
)

$localPort = 18081
adb -s $Device forward "tcp:$localPort" tcp:8080 | Out-Null
$api = "http://127.0.0.1:$localPort/api"
$headers = @{ 'X-Pin' = $Pin }
$list = (Invoke-RestMethod "$api/channels" -Headers $headers).channels |
    Where-Object { $_.enabled -and (-not $Group -or $_.group -eq $Group) }

$ok = 0
foreach ($c in $list) {
    Invoke-RestMethod -Method Post "$api/play/$([uri]::EscapeDataString($c.id))" -Headers $headers -Body '{}' | Out-Null
    Start-Sleep $WaitSeconds
    $a = Invoke-RestMethod "$api/status" -Headers $headers
    Start-Sleep $SampleSeconds
    $b = Invoke-RestMethod "$api/status" -Headers $headers

    $picture = $b.channel -eq $c.id -and $b.framesRendered -gt $a.framesRendered
    $sound = $b.channel -eq $c.id -and $b.audio -and $b.audioBuffers -gt $a.audioBuffers
    $verdict = if ($picture -and $sound) { 'OK'; $ok++ } elseif ($picture) { 'NO SOUND' } elseif ($sound) { 'NO PICTURE' } else { 'NOT PLAYING' }
    $mbps = if ($b.bitrate -gt 0) { '{0:N1} Mbps' -f ($b.bitrate / 1e6) } else { '' }
    '{0,2} {1,-20} {2,-11} video={3,-24} audio={4,-22} {5}' -f $c.number, $c.name, $verdict, $b.video, $b.audio, $mbps
}
"{0} of {1} channels OK" -f $ok, @($list).Count
