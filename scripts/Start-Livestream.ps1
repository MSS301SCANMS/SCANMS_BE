param([int]$Port=8090)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$relay=Join-Path $repo 'livestream-realtime'
$logs=Join-Path $repo '.local\livestream-realtime'
$node=(Get-Command node.exe -ErrorAction Stop).Source
if(-not (Test-Path -LiteralPath (Join-Path $relay 'node_modules\socket.io'))){throw 'Run npm.cmd ci in livestream-realtime first.'}
try {
 $health=Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 2
 if($health.service -eq 'livestream-realtime'){Write-Output "Livestream relay is already running at port $Port.";exit 0}
}catch{}
if(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue){throw "Port $Port belongs to another process. Nothing was stopped."}
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$env:PORT="$Port"
$process=Start-Process -FilePath $node -ArgumentList @('--env-file-if-exists=.env','server.mjs') -WorkingDirectory $relay -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'server.log') -RedirectStandardError (Join-Path $logs 'server.err.log') -PassThru
$process.Id | Set-Content -LiteralPath (Join-Path $logs 'pid.txt')
for($attempt=0;$attempt -lt 20;$attempt++){
 try{$health=Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 1;if($health.service -eq 'livestream-realtime'){Write-Output "Livestream relay is running at port $Port (PID $($process.Id)).";exit 0}}catch{}
 Start-Sleep -Milliseconds 250
}
throw 'Livestream relay did not start. Inspect be/.local/livestream-realtime/server.err.log.'
