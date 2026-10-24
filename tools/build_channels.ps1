# Rebuilds app/src/main/assets/default_channels.json from public playlists (iptv-org and Free-TV).
# Keeps only the wanted channels, checks every link, and merges duplicate channels into one
# entry with several fallback URLs. Run from the project root:
#   powershell -ExecutionPolicy Bypass -File tools\build_channels.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'app\src\main\assets\default_channels.json'

$sources = @(
    'https://iptv-org.github.io/iptv/index.m3u'
    'https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8'
)

# Channel list (names, groups, order) lives in its own file so it is easy to edit.
. (Join-Path $PSScriptRoot 'channel_list.ps1')

$ua = 'Mozilla/5.0 (Linux; Android 9) ParentsTV/1.0'
$http = New-Object System.Net.WebClient
$http.Encoding = [Text.Encoding]::UTF8

# Built from character codes so this file stays pure ASCII (Windows PowerShell 5 reads
# BOM-less files as ANSI): circled G = 0x24BC, circled Y = 0x24CE, circled range 0x2460-0x24FF.
$skipPattern = '\[Geo-blocked\]|' + [char]0x24BC + '|' + [char]0x24CE
$noisePattern = '\s*(\(\d{3,4}[pi]\)|\[[^\]]*\]|[' + [char]0x2460 + '-' + [char]0x24FF + '])'

# Parse #EXTINF + URL pairs from every source.
$entries = New-Object System.Collections.Generic.List[object]
foreach ($src in $sources) {
    Write-Host "Downloading $src"
    $lines = $http.DownloadString($src) -split "`n"
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i].Trim()
        if (-not $line.StartsWith('#EXTINF')) { continue }
        $rawName = $line.Substring($line.LastIndexOf(',') + 1)
        # iptv-org marks region-locked streams [Geo-blocked]; Free-TV marks them (G) and
        # YouTube-only entries (Y), which the player cannot open.
        $geo = $rawName -match $skipPattern
        $name = ($rawName -replace $noisePattern, '').Trim()
        $logo = if ($line -match 'tvg-logo="([^"]*)"') { $Matches[1] } else { '' }
        $tvgId = if ($line -match 'tvg-id="([^"]*)"') { $Matches[1] } else { '' }
        $j = $i + 1
        while ($j -lt $lines.Count -and ($lines[$j].Trim() -eq '' -or $lines[$j].Trim().StartsWith('#'))) { $j++ }
        if ($j -lt $lines.Count) { $entries.Add([pscustomobject]@{ name = $name; logo = $logo; id = $tvgId; url = $lines[$j].Trim(); geo = $geo }) }
    }
}
Write-Host "Parsed $($entries.Count) streams"

# A link counts as working when it answers 2xx and its body starts like an HLS playlist.
function Test-Stream([string]$url) {
    try {
        $req = [System.Net.HttpWebRequest]::Create($url)
        $req.UserAgent = $ua
        $req.Timeout = 12000
        $req.ReadWriteTimeout = 12000
        $req.AllowAutoRedirect = $true
        $res = $req.GetResponse()
        try {
            $buf = New-Object byte[] 1024
            $n = $res.GetResponseStream().Read($buf, 0, $buf.Length)
            return ([Text.Encoding]::ASCII.GetString($buf, 0, [Math]::Max($n, 0))).Contains('#EXTM3U')
        } finally { $res.Close() }
    } catch { return $false }
}

$channels = @()
foreach ($w in $wanted) {
    $found = @($entries | Where-Object { $_.name -match $w.match -and ($w.allowGeo -or -not $_.geo) })
    $candidates = @($w.first) + @($found | ForEach-Object { $_.url }) | Where-Object { $_ } | Select-Object -Unique
    if ($w.exclude) { $candidates = @($candidates | Where-Object { $_ -notmatch $w.exclude }) }
    $working = @($candidates | Where-Object { Test-Stream $_ })
    $status = if ($working.Count) { "$($working.Count) working of $($candidates.Count)" } else { "NO WORKING LINK (skipped)" }
    Write-Host ("{0,-20} {1}" -f $w.name, $status)
    if (-not $working.Count) { continue }

    $withId = $found | Where-Object id | Select-Object -First 1
    $id = if ($withId) { $withId.id.ToLower() } else { ($w.name.ToLower() -replace '[^a-z0-9]+', '-').Trim('-') }
    $channels += [ordered]@{
        id = $id; name = $w.name; group = $w.group
        logo = [string](@($found | Where-Object logo) | Select-Object -First 1).logo
        urls = @($working); enabled = $true
    }
}

$json = [ordered]@{ version = 1; channels = $channels } | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText($out, $json, (New-Object Text.UTF8Encoding $false))
Write-Host "Wrote $($channels.Count) channels to $out"
