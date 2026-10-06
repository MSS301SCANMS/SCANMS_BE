param([switch]$Restart)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$workspace=Split-Path $repo -Parent
& (Join-Path $PSScriptRoot 'Initialize-CommerceFinance.ps1')
$logs=Join-Path $workspace '.run-logs'
$components=@(@{name='bridge';port=3302;pattern='commerce-finance-bridge|server.mjs'},@{name='payment';port=8084;pattern='PaymentServiceApplication'},@{name='commerce';port=3000;pattern='dist[\\/]main.js'},@{name='relay';port=3301;pattern='payos-webhook-relay.mjs'})
foreach($part in $components){
  $listener=Get-NetTCPConnection -State Listen -LocalPort $part.port -ErrorAction SilentlyContinue | Select-Object -First 1
  if($listener -and $Restart){
    $existing=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if($existing.CommandLine -notmatch $part.pattern){throw "Unexpected process on port $($part.port); inspect before stopping."}
    Stop-Process -Id $listener.OwningProcess -Force
    $listener=$null
  }
  if(-not $listener){
    $launcher=Start-Process powershell.exe -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $PSScriptRoot 'Start-CommerceFinance.ps1')+'"'),'-Component',$part.name) -WorkingDirectory $workspace -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs "$($part.name)-finance.out.log") -RedirectStandardError (Join-Path $logs "$($part.name)-finance.err.log") -PassThru
    Write-Output "$($part.name) launcher PID $($launcher.Id), port $($part.port)"
  }else{Write-Output "$($part.name) already listening on $($part.port)"}
}
if(-not(Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue)){
  $npm=(Get-Command npm.cmd).Source
  $frontend=Start-Process $env:ComSpec -ArgumentList @('/d','/c',('""'+$npm+'" run dev -- --host 127.0.0.1 --port 5173 --strictPort"')) -WorkingDirectory (Join-Path $workspace 'SCANMS_FE') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'frontend.out.log') -RedirectStandardError (Join-Path $logs 'frontend.err.log') -PassThru
  Write-Output "Frontend launcher PID $($frontend.Id)"
}
Write-Output 'Web: http://127.0.0.1:5173. Finance bridge: 3302; payment-service: 8084; commerce: 3000. Existing tunnel needs to stay running for bank callbacks.'
