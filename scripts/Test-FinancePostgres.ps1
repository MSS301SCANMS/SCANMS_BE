param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [int]$Port = 55432,
    [string]$Maven = ''
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$workspace = Split-Path $repo -Parent
$data = Join-Path $workspace '.qa-postgres'
$log = Join-Path $workspace 'qa-postgres-server.log'
if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin 'initdb.exe'))) { throw 'PostgreSQL binaries not found.' }
if (-not $Maven) {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) { $Maven = $command.Source }
    else { $Maven = (Get-ChildItem (Join-Path $env:USERPROFILE '.m2\wrapper\dists') -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName }
}
if (-not $Maven) { throw 'Set -Maven to mvn.cmd.' }
if (-not (Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))) {
    if (Test-Path -LiteralPath $data) { throw 'Existing QA directory is not a PostgreSQL cluster; inspect it first.' }
    & (Join-Path $PostgresBin 'initdb.exe') -D $data -U scanms_qa --auth-local=trust --auth-host=trust --encoding=UTF8 --no-locale
    if ($LASTEXITCODE) { throw 'QA cluster initialization failed.' }
}
$pgCtl = Join-Path $PostgresBin 'pg_ctl.exe'
$result = 1
$previousPrefix = $env:SCANMS_TEST_POSTGRES_PREFIX
$previousUser = $env:SCANMS_TEST_POSTGRES_USER
$previousPassword = $env:SCANMS_TEST_POSTGRES_PASSWORD
$started = $false
Push-Location $repo
try {
    & $pgCtl -D $data status *> $null
    if ($LASTEXITCODE -eq 0) { throw 'QA cluster is already running; stop it before running this script.' }
    & $pgCtl -D $data -l $log -o "-h 127.0.0.1 -p $Port" -w start
    if ($LASTEXITCODE) { throw 'QA server could not start.' }
    $started = $true
    foreach ($name in 'users','payment','refunds','orders','product','promotion') {
        $database = "scanms_qa_$name"
        $exists = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $Port -U scanms_qa -d postgres -At -c "SELECT 1 FROM pg_database WHERE datname='$database'"
        if ($LASTEXITCODE) { throw 'QA database lookup failed.' }
        if ($exists -ne '1') {
            & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $Port -U scanms_qa $database
            if ($LASTEXITCODE) { throw 'QA database creation failed.' }
        }
    }
    $env:SCANMS_TEST_POSTGRES_PREFIX = "jdbc:postgresql://127.0.0.1:$Port/scanms_qa_"
    $env:SCANMS_TEST_POSTGRES_USER = 'scanms_qa'
    $env:SCANMS_TEST_POSTGRES_PASSWORD = ''
    & $Maven "-Dmaven.repo.local=$(Join-Path $workspace '.maven-repository')" -q test
    $result = $LASTEXITCODE
}
finally {
    $env:SCANMS_TEST_POSTGRES_PREFIX = $previousPrefix
    $env:SCANMS_TEST_POSTGRES_USER = $previousUser
    $env:SCANMS_TEST_POSTGRES_PASSWORD = $previousPassword
    if ($started) { & $pgCtl -D $data -m fast -w stop }
    Pop-Location
}
exit $result
