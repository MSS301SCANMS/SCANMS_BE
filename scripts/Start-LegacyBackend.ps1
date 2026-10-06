param([string]$BackendPath = '')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not $BackendPath) { $BackendPath = Join-Path (Split-Path $workspace -Parent) 'đồ án\backend' }
$BackendPath = (Resolve-Path -LiteralPath $BackendPath).Path
$entry = Join-Path $BackendPath 'dist\main.js'
if (-not (Test-Path -LiteralPath $entry)) { throw 'Backend build missing. Set -BackendPath to the existing SCANMS NestJS backend.' }
$package = Get-Content -LiteralPath (Join-Path $BackendPath 'package.json') -Raw | ConvertFrom-Json
if (-not $package.dependencies.'@nestjs/core' -or -not $package.dependencies.'socket.io') { throw 'Expected the SCANMS NestJS/Socket.IO backend.' }
if (Get-NetTCPConnection -State Listen -LocalPort 3000 -ErrorAction SilentlyContinue) { throw 'Port 3000 already has a listener. Inspect it before starting another backend.' }
$node = (Get-Command node.exe -ErrorAction Stop).Source
# The active workspace is the source of PayOS configuration, while NestJS retains its own DB/auth configuration.
$settings = @{}
Get-Content -LiteralPath (Join-Path $workspace 'SCANMS_BE\.env') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $settings[$matches[1].Trim()]=$matches[2].Trim().Trim('"').Trim("'") }
}
foreach ($key in 'PAYOS_CLIENT_ID','PAYOS_API_KEY','PAYOS_CHECKSUM_KEY','PAYOS_WEBHOOK_URL') {
    if (-not [string]::IsNullOrWhiteSpace($settings[$key])) { [Environment]::SetEnvironmentVariable($key,$settings[$key],'Process') }
}
if ($settings['FRONTEND_URL']) { $env:PAYOS_STOREFRONT_URL=$settings['FRONTEND_URL'] }
$logs = Join-Path $workspace '.run-logs'
New-Item -ItemType Directory -Path $logs -Force > $null
$process = Start-Process -FilePath $node -ArgumentList 'dist/main.js' -WorkingDirectory $BackendPath -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'legacy-backend.out.log') -RedirectStandardError (Join-Path $logs 'legacy-backend.err.log') -PassThru
@{pid=$process.Id;workingDirectory=$BackendPath;port=3000} | ConvertTo-Json | Set-Content (Join-Path $logs 'legacy-backend-process.json')
Write-Output "Backend launcher PID $($process.Id). Check http://localhost:3000/api/docs and .run-logs/legacy-backend.err.log."
