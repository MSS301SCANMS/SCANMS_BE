param([Parameter(Mandatory=$true)][string]$WebhookUrl,[switch]$Register)
$ErrorActionPreference='Stop'
$backend=Split-Path $PSScriptRoot -Parent
$workspace=Split-Path $backend -Parent
$uri=[Uri]$WebhookUrl
if (-not $uri.IsAbsoluteUri -or $uri.Scheme -ne 'https' -or $uri.AbsolutePath -ne '/api/v1/payments/webhooks/payos' -or $uri.IsLoopback -or $uri.Query -or $uri.Fragment -or $uri.UserInfo) { throw 'Expected a public HTTPS URL ending in /api/v1/payments/webhooks/payos.' }
$path=Join-Path $backend 'payment-service\.env'
$settings=@{}
foreach($envFile in @((Join-Path $backend '.env'),$path)) {
    if(Test-Path -LiteralPath $envFile) { Get-Content -LiteralPath $envFile | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') { $settings[$matches[1].Trim()]=$matches[2].Trim().Trim('"').Trim("'") }
    } }
}
foreach($key in 'PAYOS_CLIENT_ID','PAYOS_API_KEY','PAYOS_CHECKSUM_KEY') {
    $processValue=[Environment]::GetEnvironmentVariable($key,'Process')
    if($processValue) { $settings[$key]=$processValue }
}
foreach ($key in 'PAYOS_CLIENT_ID','PAYOS_API_KEY','PAYOS_CHECKSUM_KEY') { if (-not $settings[$key]) { throw 'PayOS configuration is incomplete.' } }
if(-not $Register) {
    Write-Output 'Local validation passed. No external changes made. Add -Register when ready to register this channel webhook.'
    return
}
$headers=@{'x-client-id'=$settings['PAYOS_CLIENT_ID'];'x-api-key'=$settings['PAYOS_API_KEY']}
try {
    $result=Invoke-RestMethod 'https://api-merchant.payos.vn/confirm-webhook' -Method Post -Headers $headers -ContentType 'application/json' -Body (@{webhookUrl=$WebhookUrl}|ConvertTo-Json -Compress) -TimeoutSec 35
} catch { throw 'PayOS webhook registration failed; credentials withheld.' }
if ($result.code -ne '00') { throw 'PayOS did not confirm this webhook. Existing local configuration preserved.' }
$source=if(Test-Path -LiteralPath $path){[IO.File]::ReadAllText($path)}else{''}
if ($source -match '(?m)^PAYOS_WEBHOOK_URL=') { $source=[regex]::Replace($source,'(?m)^PAYOS_WEBHOOK_URL=.*$','PAYOS_WEBHOOK_URL='+$WebhookUrl) }
else { $source += "`nPAYOS_WEBHOOK_URL=$WebhookUrl`n" }
[IO.File]::WriteAllText($path,$source,[Text.UTF8Encoding]::new($false))
$logDirectory=Join-Path $workspace '.run-logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
@{webhookUrl=$WebhookUrl;registered=$true} | ConvertTo-Json | Set-Content (Join-Path $logDirectory 'payos-webhook-registration.json') -Encoding UTF8
Write-Output 'PayOS confirmed the webhook. Restart payment-service to apply local configuration.'
