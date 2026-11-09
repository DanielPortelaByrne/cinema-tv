param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Package = 'com.danielportelabyrne.cinema.livetv',
    [string[]]$Channels = @('TVBrasil.br', 'DW.de', 'France24.fr', 'NHKWorldJapan.jp')
)
$ErrorActionPreference = 'Stop'
$failed = @()
# Debug APK only. This uses the real public broadcasters and Media3's actual first-video-frame event.
# No credentials, protected sessions or downloaded media; do not run on an unattended shared TV.
foreach ($channel in $Channels) {
    $since = (& adb -s $Serial shell "date '+%m-%d %H:%M:%S.000'").Trim()
    & adb -s $Serial shell am start --activity-clear-top -n "$Package/com.danielportelabyrne.cinema.CinemaActivity" --es debug_cinema_dest live --es debug_live_channel $channel | Out-Null
    $rendered = $false
    $deadline = (Get-Date).AddSeconds(90)
    do {
        Start-Sleep -Seconds 3
        $messages = & adb -s $Serial shell "logcat -d -T '$since' -s CinemaLive"
        if ($messages | Select-String -SimpleMatch "First video frame: $channel") { $rendered = $true; break }
    } while ((Get-Date) -lt $deadline)
    [pscustomobject]@{ Channel = $channel; FirstVideoFrame = $rendered; Device = $Serial }
    if (-not $rendered) { $failed += $channel }
}
if ($failed.Count -gt 0) { throw "No live video frame observed: $($failed -join ', ')" }
# A successful frame proves decoding on this device/network at this time, not future or worldwide availability.
