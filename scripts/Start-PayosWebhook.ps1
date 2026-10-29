param([string]$CloudflaredPath)
$ErrorActionPreference='Stop'
$backend=Split-Path $PSScriptRoot -Parent
$workspace=Split-Path $backend -Parent
if(-not $CloudflaredPath) {
 $installed=Get-Command cloudflared -ErrorAction SilentlyContinue
 $CloudflaredPath=if($installed){$installed.Source}else{Join-Path $workspace '.tools\cloudflared.exe'}
}
if(-not (Test-Path -LiteralPath $CloudflaredPath)){throw 'Install cloudflared or pass -CloudflaredPath.'}
$capabilities=Invoke-RestMethod 'http://127.0.0.1:8084/api/v1/finance/capabilities' -TimeoutSec 8
if(-not $capabilities.result.paymentAvailable){throw 'Start payment-service with the PayOS channel configured first.'}
try { $probe=[Net.Sockets.TcpClient]::new('127.0.0.1',3301);$probe.Dispose();throw 'Port 3301 is occupied. No existing process was stopped.' } catch [Net.Sockets.SocketException] {}
$logs=Join-Path $backend '.run-logs'
New-Item -ItemType Directory -Path $logs -Force | Out-Null
$runId=Get-Date -Format 'yyyyMMdd-HHmmss'
$relay=$null;$tunnel=$null;$success=$false
$oldAuthority=$env:FINANCE_AUTHORITY
$oldPaymentUrl=$env:FINANCE_PAYMENT_URL
try {
 $env:FINANCE_AUTHORITY='SPRING';$env:FINANCE_PAYMENT_URL='http://127.0.0.1:8084'
 $relay=Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @((Join-Path $PSScriptRoot 'payos-webhook-relay.mjs')) -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs "payos-relay-$runId.log") -RedirectStandardError (Join-Path $logs "payos-relay-$runId.err.log") -PassThru
 $tunnelLog=Join-Path $logs "payos-tunnel-$runId.err.log"
 $tunnel=Start-Process -FilePath $CloudflaredPath -ArgumentList @('tunnel','--no-autoupdate','--url','http://127.0.0.1:3301','--protocol','http2') -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs "payos-tunnel-$runId.log") -RedirectStandardError $tunnelLog -PassThru
 $publicUrl=$null
 for($attempt=0;$attempt -lt 45;$attempt++) {
  if($tunnel.HasExited){throw 'Cloudflare tunnel exited. Check the local tunnel log.'}
  $content=if(Test-Path -LiteralPath $tunnelLog){Get-Content -LiteralPath $tunnelLog -Raw}else{''}
  if($content -match 'https://[a-z0-9-]+\.trycloudflare\.com'){$publicUrl=$matches[0];break}
  Start-Sleep -Milliseconds 500
 }
 if(-not $publicUrl){throw 'Cloudflare did not provide a public URL.'}
 $webhookUrl=$publicUrl+'/api/v1/payments/webhooks/payos'
 # Let PayOS perform its signed registration probe, rather than relying on a
 # local browser/DNS resolver to declare a newly issued tunnel reachable.
 $registered=$false
 for($attempt=0;$attempt -lt 3;$attempt++) {
  try { & (Join-Path $PSScriptRoot 'Configure-PayosWebhook.ps1') -WebhookUrl $webhookUrl -Register; $registered=$true; break }
  catch { if($attempt -eq 2){throw}; Start-Sleep -Seconds 3 }
 }
 if(-not $registered){throw 'PayOS did not verify the webhook.'}
 @{webhookUrl=$webhookUrl;relayPid=$relay.Id;tunnelPid=$tunnel.Id;authority='SPRING';startedAt=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content (Join-Path $logs 'payos-webhook-processes.json') -Encoding UTF8
 $success=$true
 Write-Output "Webhook ready: $webhookUrl"
 Write-Output "Relay PID $($relay.Id); tunnel PID $($tunnel.Id). Keep both running to receive payment callbacks."
} finally {
 $env:FINANCE_AUTHORITY=$oldAuthority;$env:FINANCE_PAYMENT_URL=$oldPaymentUrl
 if(-not $success) {
  foreach($owned in @($tunnel,$relay)){if($owned -and -not $owned.HasExited){Stop-Process -Id $owned.Id -ErrorAction SilentlyContinue}}
 }
}
