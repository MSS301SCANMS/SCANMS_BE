$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$logs = Join-Path (Split-Path $repo -Parent) '.run-logs'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$java = (Get-Command java.exe -ErrorAction Stop).Source
function Read-Settings([string]$path) {
    $values = @{}
    if (Test-Path -LiteralPath $path) {
        foreach ($line in Get-Content -LiteralPath $path) {
            if ($line -match '^([^#=]+)=(.*)$') { $values[$matches[1].Trim()] = $matches[2].Trim().Trim('"').Trim("'") }
        }
    }
    return $values
}
$rootSettings = Read-Settings (Join-Path $repo '.env')
$parts = @(
    @{name='order-service'; port=8083; main='OrderServiceApplication'; prefix='ORDER'},
    @{name='payment-service'; port=8084; main='PaymentServiceApplication'; prefix='PAYMENT'},
    @{name='promotion-affiliate-service'; port=8085; main='PromotionAffiliateServiceApplication'; prefix='PROMOTION'}
)
# Validate every archive, database setting and existing listener before stopping any service.
foreach ($part in $parts) {
    $part.jar = Join-Path $repo "$($part.name)\target\$($part.name)-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $part.jar)) { throw "Build the archive first: $($part.name)" }
    if (-not $rootSettings["$($part.prefix)_DB_PORT"] -or -not $rootSettings["$($part.prefix)_DB_NAME"]) { throw "Missing local database setting: $($part.name)" }
    $listener = Get-NetTCPConnection -State Listen -LocalPort $part.port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        $running = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
        $knownMain = $running.CommandLine -match [regex]::Escape($part.main)
        $knownJar = $running.CommandLine -match [regex]::Escape($part.jar)
        $runtimePrefix = Join-Path $logs "$($part.name)-affiliate-"
        $knownRuntime = $running.CommandLine -match [regex]::Escape($runtimePrefix)
        $workspaceProcess = $running.CommandLine -match [regex]::Escape($repo)
        if ($running.Name -ne 'java.exe' -or (-not $knownRuntime -and (-not $workspaceProcess -or (-not $knownMain -and -not $knownJar)))) {
            throw "Unexpected process on port $($part.port). No services were stopped."
        }
        $part.previousPid = $running.ProcessId
    }
    $part.runtimeJar = Join-Path $logs "$($part.name)-affiliate-$stamp.jar"
    Copy-Item -LiteralPath $part.jar -Destination $part.runtimeJar
}
$started = @()
foreach ($part in $parts) {
    $settings = @{}
    foreach ($key in $rootSettings.Keys) { $settings[$key] = $rootSettings[$key] }
    $local = Read-Settings (Join-Path $repo "$($part.name)\.env")
    foreach ($key in $local.Keys) { $settings[$key] = $local[$key] }
    foreach ($key in $settings.Keys) { [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process') }
    $databasePort = $rootSettings["$($part.prefix)_DB_PORT"]
    $databaseName = $rootSettings["$($part.prefix)_DB_NAME"]
    $env:DB_URL = "jdbc:postgresql://localhost:$databasePort/$databaseName"
    $env:DB_USERNAME = $rootSettings['POSTGRES_USER']
    $env:DB_PASSWORD = $rootSettings['POSTGRES_PASSWORD']
    $env:SERVER_PORT = [string]$part.port
    if ($part.previousPid) { Stop-Process -Id $part.previousPid -Force }
    $out = Join-Path $logs "$($part.name)-affiliate-$stamp.out.log"
    $err = Join-Path $logs "$($part.name)-affiliate-$stamp.err.log"
    $arguments = @('-Xms128m','-Xmx512m','-jar',('"' + $part.runtimeJar + '"'),"--server.port=$($part.port)",'--logging.level.root=INFO','--logging.level.org.springframework.web=INFO','--logging.level.org.hibernate=INFO')
    $running = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $repo -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    $started += @{service=$part.name; port=$part.port; pid=$running.Id; stdout=$out; stderr=$err}
    Write-Output "$($part.name): started PID $($running.Id) on port $($part.port)"
}
$started | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logs 'affiliate-runtime-services.json') -Encoding UTF8
