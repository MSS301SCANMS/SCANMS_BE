$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $repo '.local/intellij'
$envPath=Join-Path $runtime 'livestream-realtime.env'
if(-not(Test-Path -LiteralPath $envPath)){throw 'Run Prepare-IntelliJ.ps1 first to generate relay settings.'}
$manifestPath=Join-Path $runtime 'relay-process.json'
$hash=(Get-FileHash -LiteralPath $envPath -Algorithm SHA256).Hash
$listener=Get-NetTCPConnection -State Listen -LocalPort 8090 -ErrorAction SilentlyContinue|Select-Object -First 1
if($listener){
    $healthy=$false
    try{$healthy=(Invoke-RestMethod 'http://127.0.0.1:8090/health' -TimeoutSec 3).service -eq 'livestream-realtime'}catch{}
    if($healthy -and (Test-Path -LiteralPath $manifestPath)){
        $tracked=Get-Content -LiteralPath $manifestPath -Raw|ConvertFrom-Json
        if($tracked.pid -eq $listener.OwningProcess -and $tracked.settingsHash -eq $hash){Write-Output '[SCANMS] Chat/live relay already ready on 8090.';return}
        $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
        $entry=Join-Path $repo 'livestream-realtime/server.mjs'
        if($tracked.pid -eq $process.ProcessId -and $process.Name -eq 'node.exe' -and $process.CommandLine.Contains($entry)){
            Stop-Process -Id $process.ProcessId
        }else{throw 'Port 8090 belongs to an untracked process; stop that relay before changing its settings.'}
    }elseif($healthy){Write-Output '[SCANMS] Existing chat/live relay ready on 8090; left unchanged.';return}
    else{throw 'Port 8090 is occupied by another process; no process was stopped.'}
}
$node=(Get-Command node.exe -ErrorAction Stop).Source
$entry=Join-Path $repo 'livestream-realtime/server.mjs'
if(-not(Test-Path -LiteralPath (Join-Path $repo 'livestream-realtime/node_modules/socket.io'))){throw 'Install relay dependencies: run npm ci in livestream-realtime.'}
$process=Start-Process -FilePath $node -ArgumentList @('--env-file="'+$envPath+'"','"'+$entry+'"') -WorkingDirectory (Join-Path $repo 'livestream-realtime') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'relay.out.log') -RedirectStandardError (Join-Path $runtime 'relay.err.log')
@{pid=$process.Id;settingsHash=$hash}|ConvertTo-Json|Set-Content -LiteralPath $manifestPath -Encoding UTF8
$until=[DateTime]::UtcNow.AddSeconds(15)
while([DateTime]::UtcNow -lt $until){
    try{if((Invoke-RestMethod 'http://127.0.0.1:8090/health' -TimeoutSec 2).service -eq 'livestream-realtime'){Write-Output '[SCANMS] Chat/live relay ready on 8090.';return}}catch{}
    if($process.HasExited){throw 'Relay exited; inspect .local/intellij/relay.err.log.'}
    Start-Sleep -Milliseconds 400
}
throw 'Relay did not become ready on 8090; inspect .local/intellij/relay.err.log.'
