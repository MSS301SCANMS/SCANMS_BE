param([Parameter(Mandatory=$true)][string]$WebhookUrl)
$ErrorActionPreference='Stop'
$workspace=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$uri=[Uri]$WebhookUrl
if ($uri.Scheme -ne 'https' -or $uri.AbsolutePath -ne '/api/orders/payos/webhook' -or $uri.Query -or $uri.Fragment -or $uri.UserInfo) { throw 'Expected a public HTTPS URL ending in /api/orders/payos/webhook.' }
$path=Join-Path $workspace 'SCANMS_BE\.env'
$settings=@{}
Get-Content -LiteralPath $path | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $settings[$matches[1].Trim()]=$matches[2].Trim().Trim('"').Trim("'") }
}
foreach ($key in 'PAYOS_CLIENT_ID','PAYOS_API_KEY','PAYOS_CHECKSUM_KEY') { if (-not $settings[$key]) { throw 'PayOS configuration is incomplete.' } }
# Run only after confirming this channel may replace its current webhook.
$headers=@{'x-client-id'=$settings['PAYOS_CLIENT_ID'];'x-api-key'=$settings['PAYOS_API_KEY']}
try {
    $result=Invoke-RestMethod 'https://api-merchant.payos.vn/confirm-webhook' -Method Post -Headers $headers -ContentType 'application/json' -Body (@{webhookUrl=$WebhookUrl}|ConvertTo-Json -Compress) -TimeoutSec 35
} catch { throw 'PayOS webhook registration failed; credentials withheld.' }
if ($result.code -ne '00') { throw 'PayOS did not confirm this webhook. Existing local configuration preserved.' }
$source=[IO.File]::ReadAllText($path)
if ($source -match '(?m)^PAYOS_WEBHOOK_URL=') { $source=[regex]::Replace($source,'(?m)^PAYOS_WEBHOOK_URL=.*$','PAYOS_WEBHOOK_URL='+$WebhookUrl) }
else { $source += "`nPAYOS_WEBHOOK_URL=$WebhookUrl`n" }
[IO.File]::WriteAllText($path,$source,[Text.UTF8Encoding]::new($false))
@{webhookUrl=$WebhookUrl;registered=$true} | ConvertTo-Json | Set-Content (Join-Path $workspace '.run-logs\payos-webhook-registration.json') -Encoding UTF8
Write-Output 'PayOS confirmed the webhook. Restart the legacy backend to apply readiness configuration.'
