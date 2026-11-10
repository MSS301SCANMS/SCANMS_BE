param([int]$TimeoutSeconds = 180)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $repo '.local/intellij'
[void][IO.Directory]::CreateDirectory($runtime)
function Read-Env([string]$path) {
    $values = @{}
    if (Test-Path -LiteralPath $path) {
        foreach ($line in Get-Content -LiteralPath $path) {
            if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $values[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'") }
        }
    }
    return $values
}
function Wait-Ready([string]$name, [scriptblock]$check) {
    $until = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $until) {
        if (& $check) { return }
        Start-Sleep -Seconds 2
    }
    throw "$name did not become ready. Check Docker Desktop and container logs, then Run again."
}
function Test-DockerReady {
    try { & docker info --format '{{.ServerVersion}}' *> $null; return $LASTEXITCODE -eq 0 } catch { return $false }
}
$lock = $null
$until = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds * 2)
while (-not $lock) {
    try { $lock = [IO.File]::Open((Join-Path $runtime 'startup.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
    catch [IO.IOException] {
        if ([DateTime]::UtcNow -gt $until) { throw 'Another startup preparation did not finish in time.' }
        Start-Sleep -Seconds 1
    }
}
try {
    $root = Read-Env (Join-Path $repo '.env')
    if (-not (Test-DockerReady)) {
        $desktop = Join-Path $env:ProgramFiles 'Docker/Docker/Docker Desktop.exe'
        if (-not (Test-Path -LiteralPath $desktop)) { throw 'Install Docker Desktop before running the local services.' }
        Write-Output '[SCANMS] Starting Docker Desktop...'
        Start-Process -FilePath $desktop -WindowStyle Hidden
        Wait-Ready 'Docker Engine' { Test-DockerReady }
    }
    $databaseNames = @('keycloak-user-db-1','keycloak-product-db-1','keycloak-order-db-1','keycloak-payment-db-1','keycloak-promotion-db-1','keycloak-keycloak-db-1')
    $names = @(& docker ps -a --format '{{.Names}}')
    if (@($databaseNames + @('keycloak-keycloak-1') | Where-Object { $_ -notin $names }).Count) {
        & docker compose --env-file (Join-Path $repo '.env') -p keycloak -f (Join-Path $repo 'keycloak/docker-compose.yml') up -d
        if ($LASTEXITCODE -ne 0) { throw 'Could not start the Keycloak/database Compose project.' }
    }
    $names = @(& docker ps -a --format '{{.Names}}')
    if ('scanms_be-redis-1' -notin $names -or 'scanms_be-kafka-1' -notin $names) {
        & docker compose --env-file (Join-Path $repo '.env') -p scanms_be -f (Join-Path $repo 'docker-compose.yml') up -d
        if ($LASTEXITCODE -ne 0) { throw 'Could not start Redis/Kafka Compose project.' }
    }
    foreach ($name in $databaseNames + @('scanms_be-redis-1','scanms_be-kafka-1','keycloak-keycloak-1')) {
        $state = (& docker inspect $name | ConvertFrom-Json)[0].State
        if (-not $state.Running) {
            Write-Output "[SCANMS] Starting $name"
            & docker start $name | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Could not start $name" }
        }
    }
    Write-Output '[SCANMS] Waiting for databases, Redis and Keycloak...'
    foreach ($name in $databaseNames + @('scanms_be-redis-1')) {
        Wait-Ready $name {
            $state = (& docker inspect $name | ConvertFrom-Json)[0].State
            return $state.Running -and (-not $state.Health -or $state.Health.Status -eq 'healthy')
        }
    }
    $kcBase = 'http://localhost:8180'
    Wait-Ready 'Keycloak realm scanms' {
        try { $null = Invoke-RestMethod "$kcBase/realms/scanms/.well-known/openid-configuration" -TimeoutSec 4; return $true } catch { return $false }
    }
    $discovery = Invoke-RestMethod "$kcBase/realms/scanms/.well-known/openid-configuration" -TimeoutSec 10
    $kc = (& docker inspect keycloak-keycloak-1 | ConvertFrom-Json)[0]
    $kcEnv = @{}
    foreach ($entry in $kc.Config.Env) { $pair = $entry.Split('=',2); $kcEnv[$pair[0]] = $pair[1] }
    $admin = Invoke-RestMethod "$kcBase/realms/master/protocol/openid-connect/token" -Method Post -TimeoutSec 15 -Body @{
        grant_type='password'; client_id='admin-cli'; username=$kcEnv['KEYCLOAK_ADMIN']; password=$kcEnv['KEYCLOAK_ADMIN_PASSWORD']
    }
    $headers = @{ Authorization="Bearer $($admin.access_token)" }
    $clientSecrets = @{}
    foreach ($pair in @(@('CLIENT_SECRET','scanms-client'),@('ORDER_SERVICE_CLIENT_SECRET','order-service'),@('PAYMENT_SERVICE_CLIENT_SECRET','payment-service'),@('AFFILIATE_SERVICE_CLIENT_SECRET','promotion-affiliate-service'))) {
        $clientId = $pair[1]
        $clients = Invoke-RestMethod "$kcBase/admin/realms/scanms/clients?clientId=$clientId" -Headers $headers -TimeoutSec 10
        $client = $clients | Where-Object clientId -eq $clientId | Select-Object -First 1
        if (-not $client) { throw "Keycloak client $clientId is missing. Import the project's realm before running." }
        $secret = Invoke-RestMethod "$kcBase/admin/realms/scanms/clients/$($client.id)/client-secret" -Headers $headers -TimeoutSec 10
        $clientSecrets[$pair[0]] = $secret.value
    }
    $secretPath = Join-Path $runtime 'live-metrics-secret.txt'
    $demoSecret = Join-Path (Split-Path $repo -Parent) '.run-logs/demo-public/metrics-secret.txt'
    if (-not (Test-Path -LiteralPath $secretPath)) {
        $value = if (Test-Path -LiteralPath $demoSecret) { (Get-Content -LiteralPath $demoSecret -Raw).Trim() } elseif ($root['LIVE_METRICS_SECRET']) { $root['LIVE_METRICS_SECRET'] } else { [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N') }
        [IO.File]::WriteAllText($secretPath, $value)
    }
    $metrics = (Get-Content -LiteralPath $secretPath -Raw).Trim()
    $parts = @(@('user-service','USER',8081),@('product-service','PRODUCT',8082),@('order-service','ORDER',8083),@('payment-service','PAYMENT',8084),@('promotion-affiliate-service','PROMOTION',8085),@('scanms-gateway','',8080))
    foreach ($part in $parts) {
        $module=$part[0]; $prefix=$part[1]
        $settings=@{}; foreach($key in $root.Keys) { $settings[$key]=$root[$key] }
        $moduleEnv=Read-Env (Join-Path $repo "$module/.env")
        foreach($key in $moduleEnv.Keys) { $settings[$key]=$moduleEnv[$key] }
        foreach($key in $clientSecrets.Keys) { $settings[$key]=$clientSecrets[$key] }
        # Read the issuer locally: starting the IDE never requires an active public tunnel.
        $settings['KEYCLOAK_ISSUER_URI']=$discovery.issuer
        $settings['SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI']="$kcBase/realms/scanms/protocol/openid-connect/certs"
        $settings['FRONTEND_URL']=if($root['FRONTEND_URL']){$root['FRONTEND_URL']}else{'http://localhost:5173'}
        $settings['FINANCE_TOKEN_URL']="$kcBase/realms/scanms/protocol/openid-connect/token"
        $settings['PAYMENT_SERVICE_TOKEN_URL']=$settings['FINANCE_TOKEN_URL']
        $settings['LIVE_METRICS_SECRET']=$metrics
        $settings['AUTOMATIC_PAYOUTS']='false'
        $settings['SERVER_PORT']=[string]$part[2]; $settings['SERVER_ADDRESS']='0.0.0.0'
        $settings['SERVER_FORWARD_HEADERS_STRATEGY']='framework'; $settings['DEBUG']='false'
        foreach($service in $parts) { if($service[1]) { $settings["$($service[1])_SERVICE_URL"]="http://localhost:$($service[2])" } }
        if($prefix) {
            if(-not $settings['DB_URL']) { $settings['DB_URL']="jdbc:postgresql://localhost:$($settings["${prefix}_DB_PORT"])/$($settings["${prefix}_DB_NAME"])" }
            if(-not $settings['DB_USERNAME']) { $settings['DB_USERNAME']=$settings['POSTGRES_USER'] }
            if(-not $settings['DB_PASSWORD']) { $settings['DB_PASSWORD']=$settings['POSTGRES_PASSWORD'] }
        }
        $envPath=Join-Path $runtime "$module.env"
        $content=($settings.Keys|Sort-Object|ForEach-Object {"$_=$($settings[$_])"}) -join "`n"
        [IO.File]::WriteAllText($envPath,$content+"`n",(New-Object Text.UTF8Encoding($false)))
        # Recover resources after an IDE output-directory change or a Maven clean.
        $resources=Join-Path $repo "$module/src/main/resources"
        $classes=Join-Path $repo "$module/target/classes"
        if(Test-Path -LiteralPath $resources) {
            foreach($file in Get-ChildItem -LiteralPath $resources -File -Recurse) {
                $relative=$file.FullName.Substring($resources.Length).TrimStart('\','/')
                $target=Join-Path $classes $relative
                if(-not(Test-Path -LiteralPath $target) -or (Get-Item -LiteralPath $target).LastWriteTimeUtc -lt $file.LastWriteTimeUtc) {
                    [void][IO.Directory]::CreateDirectory((Split-Path $target -Parent))
                    Copy-Item -LiteralPath $file.FullName -Destination $target -Force
                }
            }
        }
    }
    try { & (Join-Path $PSScriptRoot 'Ensure-PublicDemo.ps1') }
    catch { Write-Warning "Public demo is offline: $($_.Exception.Message) Local Spring services can still start." }
    $origins=@(
        'http://localhost:5173',
        'http://127.0.0.1:5173',
        'https://vercel-upload-20261107-1.vercel.app'
    )
    $issuerUri=[Uri]$discovery.issuer
    $origins+=$issuerUri.GetLeftPart([UriPartial]::Authority)
    $publicConfig=Join-Path $runtime 'public-demo.json'
    if(Test-Path -LiteralPath $publicConfig) {
        $publicUrl=(Get-Content -LiteralPath $publicConfig -Raw|ConvertFrom-Json).url
        if($publicUrl) {
            $publicUri=[Uri]$publicUrl
            if($publicUri.Scheme -ne 'https' -or $publicUri.AbsolutePath -ne '/') { throw 'Public demo URL must be an HTTPS origin without a path.' }
            $origins+=$publicUri.GetLeftPart([UriPartial]::Authority)
        }
    }
    $relay=@{
        HOST='0.0.0.0'; PORT='8090'; API_URL='http://localhost:8080/api/v1'
        KEYCLOAK_ISSUER=$discovery.issuer
        KEYCLOAK_JWKS_URL="$kcBase/realms/scanms/protocol/openid-connect/certs"
        ALLOWED_ORIGINS=(($origins|Select-Object -Unique) -join ',')
        LIVE_METRICS_SECRET=$metrics
    }
    [IO.File]::WriteAllText((Join-Path $runtime 'livestream-realtime.env'),(($relay.Keys|Sort-Object|ForEach-Object {"$_=$($relay[$_])"}) -join "`n")+"`n",(New-Object Text.UTF8Encoding($false)))
    & (Join-Path $PSScriptRoot 'Start-LocalRelay.ps1')
    Write-Output '[SCANMS] Dependencies and chat/live relay ready. IntelliJ environments refreshed; no public tunnel is required.'
} finally { if($lock) { $lock.Dispose() } }
