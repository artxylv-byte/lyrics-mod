# Artlyrics now-playing helper for Windows.
#
# Reads the "System Media Transport Controls" (the same info the Windows volume pop-up shows for
# Spotify, browsers, etc.) and prints one JSON line per update to stdout. Started by the Artlyrics
# Minecraft mod; it exits on its own when Minecraft closes. It reads track info (and saves the track's cover
# picture into the mod's folder), and only when the player uses Artlyrics' media controls does it play / pause
# or skip, through the same Windows controls as the media keys. It never touches the network.
param(
    [int]$ParentPid = 0,
    [string]$Prefer = 'spotify',
    [string]$CoverDir = ''
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

# Cover pictures are optional: if anything about them fails, track info still works.
$streamType = $null
try {
    $null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]
} catch {
    $streamType = $null
}

# Returns the track's thumbnail as bytes, or $null.
function Read-Cover($props) {
    if ($null -eq $streamType -or -not $CoverDir -or $null -eq $props.Thumbnail) { return $null }
    $winStream = Await ($props.Thumbnail.OpenReadAsync()) $streamType
    try {
        $netStream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($winStream)
        $buffer = New-Object System.IO.MemoryStream
        $netStream.CopyTo($buffer)
        $netStream.Dispose()
        return ,$buffer.ToArray()
    } finally {
        $winStream.Dispose()
    }
}

# Commands from Artlyrics arrive one per line on standard input: toggle, next, previous.
$commandReader = $null
$pendingCommand = $null
try {
    $commandReader = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
    $pendingCommand = $commandReader.ReadLineAsync()
} catch {
    $pendingCommand = $null
}

$coverKey = ''
$coverPath = ''
$coverHash = ''
$coverSerial = 0
$coverChecks = 0

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

        # Carry out any commands that came in.
        while ($null -ne $pendingCommand -and $pendingCommand.IsCompleted) {
            $command = $null
            try { $command = $pendingCommand.Result } catch { }
            if ($null -eq $command) {
                $pendingCommand = $null # Artlyrics closed our input: no more commands
                break
            }
            $pendingCommand = $commandReader.ReadLineAsync()
            if ($null -ne $pick) {
                try {
                    switch ($command.Trim()) {
                        'toggle' { $null = Await ($pick.TryTogglePlayPauseAsync()) ([bool]) }
                        'next' { $null = Await ($pick.TrySkipNextAsync()) ([bool]) }
                        'previous' { $null = Await ($pick.TrySkipPreviousAsync()) ([bool]) }
                    }
                } catch {
                    # the player didn't accept it
                }
            }
        }

        if ($null -eq $pick) {
            Emit @{ none = $true }
        } else {
            $props = Await ($pick.TryGetMediaPropertiesAsync()) $propsType
            $timeline = $pick.GetTimelineProperties()

            # The cover: read when the song changes, and a few more times shortly after, because players
            # often send the picture a moment after the title. Saved under a new name whenever it changes.
            $key = "$($pick.SourceAppUserModelId)|$($props.Title)|$($props.Artist)|$($props.AlbumTitle)"
            if ($key -ne $coverKey) {
                $coverKey = $key
                $coverChecks = 0
                $coverPath = ''
                $coverHash = ''
            }
            if ($coverChecks -lt 4 -and ($coverChecks -eq 0 -or ($tick % 3) -eq 0)) {
                $coverChecks++
                try {
                    $bytes = Read-Cover $props
                    if ($null -ne $bytes -and $bytes.Length -gt 0) {
                        $hash = [System.BitConverter]::ToString([System.Security.Cryptography.MD5]::Create().ComputeHash($bytes))
                        if ($hash -ne $coverHash) {
                            $coverSerial++
                            $file = Join-Path $CoverDir ('cover-' + ($coverSerial % 4) + '.img')
                            [System.IO.File]::WriteAllBytes($file, $bytes)
                            $coverHash = $hash
                            $coverPath = $file
                        }
                    }
                } catch {
                    # no picture this time
                }
            }

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
                cover     = [string]$coverPath
            })
        }
    } catch {
        Emit @{ error = "$($_.Exception.Message)" }
    }
    # Wait for the next update, but wake up at once when a command arrives.
    if ($null -ne $pendingCommand) {
        try { $null = $pendingCommand.Wait(400) } catch { Start-Sleep -Milliseconds 400 }
    } else {
        Start-Sleep -Milliseconds 400
    }
}
