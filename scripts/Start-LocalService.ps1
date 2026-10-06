param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('user-service','product-service','order-service','payment-service','promotion-affiliate-service','scanms-gateway')]
    [string]$Service,
    [int]$DatabasePort = 0,
    [string]$Maven = ''
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$workspace = Split-Path $repo -Parent
$settings = @{}
Get-Content -LiteralPath (Join-Path $repo '.env') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') {
        $key=$matches[1].Trim(); $value=$matches[2].Trim().Trim('"').Trim("'")
        $settings[$key]=$value
        [Environment]::SetEnvironmentVariable($key,$value,'Process')
    }
}
$prefix = @{ 'user-service'='USER';'product-service'='PRODUCT';'order-service'='ORDER';'payment-service'='PAYMENT';'promotion-affiliate-service'='PROMOTION' }[$Service]
if ($prefix) {
    if (-not $DatabasePort) { $DatabasePort=[int]$settings["${prefix}_DB_PORT"] }
    $database=$settings["${prefix}_DB_NAME"]
    if (-not $database -or -not $DatabasePort) { throw 'Missing database name or port in .env.' }
    $env:DB_URL="jdbc:postgresql://localhost:$DatabasePort/$database"
    $env:DB_USERNAME=$settings['POSTGRES_USER']
    $env:DB_PASSWORD=$settings['POSTGRES_PASSWORD']
    Write-Output "$Service -> localhost:$DatabasePort/$database"
}
if (-not $Maven) {
    $command=Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) { $Maven=$command.Source }
    else { $Maven=(Get-ChildItem (Join-Path $env:USERPROFILE '.m2\wrapper\dists') -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName }
}
if (-not $Maven) { throw 'Set -Maven to mvn.cmd.' }
Push-Location $repo
try { & $Maven "-Dmaven.repo.local=$(Join-Path $workspace '.maven-repository')" -pl $Service spring-boot:run; $result=$LASTEXITCODE }
finally { Pop-Location }
exit $result
