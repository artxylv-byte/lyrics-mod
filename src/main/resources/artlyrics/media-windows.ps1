# Artlyrics now-playing helper for Windows.
#
# Reads the "System Media Transport Controls" (the same info the Windows volume pop-up shows for
# Spotify, browsers, etc.) and prints one JSON line per update to stdout. Started by the Artlyrics
# Minecraft mod; it exits on its own when Minecraft closes. It only reads track info, it never
# controls playback and never touches the network.
param(
    [int]$ParentPid = 0,
    [string]$Prefer = 'spotify'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Emit($obj) {
    [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress))
    [Console]::Out.Flush()
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

    function Await($operation, [Type]$resultType) {
        $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($operation))
        $null = $task.Wait(-1)
        $task.Result
    }

    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
    $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
    $propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
    $manager = Await ($managerType::RequestAsync()) $managerType
} catch {
    Emit @{ error = "Windows media controls unavailable: $($_.Exception.Message)" }
    exit 2
}

$tick = 0
while ($true) {
    $tick++
    if ($ParentPid -gt 0 -and ($tick % 10) -eq 0) {
        if (-not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { exit 0 }
    }

    try {
        $sessions = @($manager.GetSessions())
        $pick = $null
        # 1) the preferred app if it's playing, 2) anything playing, 3) whatever Windows considers current.
        foreach ($s in $sessions) {
            if ($Prefer -and $s.SourceAppUserModelId -like "*$Prefer*" -and [int]$s.GetPlaybackInfo().PlaybackStatus -eq 4) { $pick = $s; break }
        }
        if ($null -eq $pick) {
            foreach ($s in $sessions) {
                if ([int]$s.GetPlaybackInfo().PlaybackStatus -eq 4) { $pick = $s; break }
            }
        }
        if ($null -eq $pick) { $pick = $manager.GetCurrentSession() }

        if ($null -eq $pick) {
            Emit @{ none = $true }
        } else {
            $props = Await ($pick.TryGetMediaPropertiesAsync()) $propsType
            $timeline = $pick.GetTimelineProperties()
            Emit ([ordered]@{
                app       = [string]$pick.SourceAppUserModelId
                title     = [string]$props.Title
                artist    = [string]$props.Artist
                album     = [string]$props.AlbumTitle
                status    = [int]$pick.GetPlaybackInfo().PlaybackStatus
                posMs     = [long]$timeline.Position.TotalMilliseconds
                endMs     = [long]$timeline.EndTime.TotalMilliseconds
                updatedMs = [long]$timeline.LastUpdatedTime.ToUnixTimeMilliseconds()
                nowMs     = [long][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            })
        }
    } catch {
        Emit @{ error = "$($_.Exception.Message)" }
    }
    Start-Sleep -Milliseconds 400
}
