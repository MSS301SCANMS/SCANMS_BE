param([int]$Port=55433,[string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin')
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$workspace=Split-Path $repo -Parent
$envFile=Join-Path $repo '.env'
$settings=@{}
Get-Content -LiteralPath $envFile | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$'){$settings[$matches[1].Trim()]=$matches[2].Trim().Trim('"').Trim("'")}}
$data=Join-Path $workspace '.dev-finance-postgres'
$passwordFile=Join-Path $workspace '.run-logs\finance-init-password.tmp'
New-Item -ItemType Directory -Path (Join-Path $workspace '.run-logs') -Force > $null
function New-Secret([int]$Length=32){$bytes=New-Object byte[] $Length; $rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($bytes)}finally{$rng.Dispose()};return [Convert]::ToBase64String($bytes)}
if(-not(Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))){
  if(Test-Path -LiteralPath $data){throw 'Existing development directory is not a PostgreSQL cluster; inspect it first.'}
  $settings['FINANCE_DB_PASSWORD']=New-Secret
  [IO.File]::WriteAllText($passwordFile,$settings['FINANCE_DB_PASSWORD'],[Text.UTF8Encoding]::new($false))
  try { & (Join-Path $PostgresBin 'initdb.exe') -D $data -U scanms_finance --auth-local=scram-sha-256 --auth-host=scram-sha-256 --pwfile=$passwordFile --encoding=UTF8 --no-locale; if($LASTEXITCODE){throw 'Development cluster initialization failed.'} }
  finally{Remove-Item -LiteralPath $passwordFile -Force -ErrorAction SilentlyContinue}
}elseif(-not $settings['FINANCE_DB_PASSWORD']){throw 'Existing finance cluster password missing; do not reset it.'}
$settings['FINANCE_DB_PORT']=[string]$Port
$settings['FINANCE_DB_USERNAME']='scanms_finance'
$settings['FINANCE_DB_URL']="jdbc:postgresql://127.0.0.1:$Port/scanms_finance_dev"
if(-not $settings['BANK_ENCRYPTION_KEY']){$settings['BANK_ENCRYPTION_KEY']=New-Secret}
if(-not $settings['PAYMENT_SERVICE_CLIENT_SECRET']){$settings['PAYMENT_SERVICE_CLIENT_SECRET']=New-Secret}
$settings['PAYMENT_SERVICE_CLIENT_ID']='payment-service'
$settings['PAYMENT_SERVICE_TOKEN_URL']='http://127.0.0.1:3302/oauth/token'
$settings['FINANCE_PAYMENT_URL']='http://127.0.0.1:8084'
$settings['FINANCE_AUTHORITY']='SPRING'
$settings['FINANCE_BRIDGE_DISPATCH']='true'
if(-not $settings['COMMERCE_DATABASE_URL']){
  $legacyConfig=Join-Path (Split-Path $workspace -Parent) 'đồ án\backend\.env'
  if(-not(Test-Path -LiteralPath $legacyConfig)){throw 'Existing commerce configuration not found. Set COMMERCE_DATABASE_URL privately in .env.'}
  Get-Content -LiteralPath $legacyConfig | ForEach-Object{if($_ -match '^DATABASE_URL=(.*)$'){$settings['COMMERCE_DATABASE_URL']=$matches[1].Trim().Trim('"').Trim("'")}}
  if(-not $settings['COMMERCE_DATABASE_URL']){throw 'Commerce database URL not found.'}
}
$text=[IO.File]::ReadAllText($envFile,[Text.Encoding]::UTF8)
foreach($key in 'FINANCE_DB_PASSWORD','FINANCE_DB_PORT','FINANCE_DB_USERNAME','FINANCE_DB_URL','BANK_ENCRYPTION_KEY','PAYMENT_SERVICE_CLIENT_ID','PAYMENT_SERVICE_CLIENT_SECRET','PAYMENT_SERVICE_TOKEN_URL','FINANCE_PAYMENT_URL','FINANCE_AUTHORITY','FINANCE_BRIDGE_DISPATCH','COMMERCE_DATABASE_URL'){
  $line="$key=$($settings[$key])"
  if($text -match "(?m)^$key=.*$"){$text=[regex]::Replace($text,"(?m)^$key=[^\r\n]*",[System.Text.RegularExpressions.MatchEvaluator]{param($m)$line})}else{$text+="`r`n$line"}
}
[IO.File]::WriteAllText($envFile,$text,[Text.UTF8Encoding]::new($false))
$pgCtl=Join-Path $PostgresBin 'pg_ctl.exe'
& $pgCtl -D $data status *> $null
if($LASTEXITCODE -ne 0){& $pgCtl -D $data -l (Join-Path $workspace '.run-logs\finance-postgres.log') -o "-h 127.0.0.1 -p $Port" -w start;if($LASTEXITCODE){throw 'Finance PostgreSQL start failed.'}}
$previous=$env:PGPASSWORD
try{
  $env:PGPASSWORD=$settings['FINANCE_DB_PASSWORD']
  $exists=& (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $Port -U scanms_finance -d postgres -At -c "SELECT 1 FROM pg_database WHERE datname='scanms_finance_dev'"
  if($LASTEXITCODE){throw 'Finance database credential check failed.'}
  if($exists -ne '1'){& (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $Port -U scanms_finance scanms_finance_dev;if($LASTEXITCODE){throw 'Finance database creation failed.'}}
}finally{$env:PGPASSWORD=$previous}
Write-Output "Dedicated persistent finance PostgreSQL ready at 127.0.0.1:$Port. Configuration saved to SCANMS_BE/.env; secrets are not printed. Existing PostgreSQL is unchanged."
