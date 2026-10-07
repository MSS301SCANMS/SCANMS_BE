param([ValidateSet('bridge','payment','commerce','relay')][string]$Component,[string]$Maven='')
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$workspace=Split-Path $repo -Parent
$settings=@{}
Get-Content -LiteralPath (Join-Path $repo '.env') | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$'){$settings[$matches[1].Trim()]=$matches[2].Trim().Trim('"').Trim("'")}}
foreach($key in 'COMMERCE_DATABASE_URL','BANK_ENCRYPTION_KEY','PAYMENT_SERVICE_CLIENT_ID','PAYMENT_SERVICE_CLIENT_SECRET','PAYMENT_SERVICE_TOKEN_URL','FINANCE_PAYMENT_URL','FINANCE_AUTHORITY','FINANCE_BRIDGE_DISPATCH','PAYOS_CLIENT_ID','PAYOS_API_KEY','PAYOS_CHECKSUM_KEY','PAYOS_WEBHOOK_URL','PAYOS_PAYOUT_CLIENT_ID','PAYOS_PAYOUT_API_KEY','PAYOS_PAYOUT_CHECKSUM_KEY','BANK_CLOSED_DATES','FRONTEND_URL'){
  if($settings[$key]){[Environment]::SetEnvironmentVariable($key,$settings[$key],'Process')}
}
switch($Component){
  'bridge'{Push-Location (Join-Path $repo 'commerce-finance-bridge');try{& node.exe server.mjs}finally{Pop-Location}}
  'relay'{& node.exe (Join-Path $PSScriptRoot 'payos-webhook-relay.mjs')}
  'commerce'{
    $env:DATABASE_URL=$settings['COMMERCE_DATABASE_URL']
    $env:FINANCE_AUTHORITY='SPRING'
    # Existing commerce auth/mail settings stay in its original private configuration. Never copy secrets into source.
    $legacyConfig=Join-Path (Split-Path $workspace -Parent) 'đồ án\backend\.env'
    if(Test-Path -LiteralPath $legacyConfig){Get-Content -LiteralPath $legacyConfig | ForEach-Object{if($_ -match '^([^#=]+)=(.*)$' -and $matches[1].Trim() -notmatch '^(DATABASE_URL|PAYOS_|PORT|FINANCE_)'){[Environment]::SetEnvironmentVariable($matches[1].Trim(),$matches[2].Trim().Trim('"').Trim("'"),'Process')}}}
    $env:PORT='3000';$env:PAYOS_STOREFRONT_URL=$settings['FRONTEND_URL']
    Push-Location (Join-Path $repo 'legacy-commerce');try{& node.exe dist/main.js}finally{Pop-Location}
  }
  'payment'{
    if(-not $settings['FINANCE_DB_URL'] -or -not $settings['BANK_ENCRYPTION_KEY']){throw 'Run Initialize-CommerceFinance.ps1 first.'}
    $env:DB_URL=$settings['FINANCE_DB_URL'];$env:DB_USERNAME=$settings['FINANCE_DB_USERNAME'];$env:DB_PASSWORD=$settings['FINANCE_DB_PASSWORD']
    $env:SERVER_PORT='8084';$env:SERVER_ADDRESS='127.0.0.1';$env:SPRING_PROFILES_ACTIVE='commerce-bridge'
    $env:USER_SERVICE_URL='http://127.0.0.1:3302/contracts';$env:PRODUCT_SERVICE_URL=$env:USER_SERVICE_URL;$env:ORDER_SERVICE_URL=$env:USER_SERVICE_URL;$env:PROMOTION_SERVICE_URL=$env:USER_SERVICE_URL
    $env:KEYCLOAK_ISSUER_URI='http://127.0.0.1:3302';$env:AUTOMATIC_PAYOUTS=if($settings['AUTOMATIC_PAYOUTS']){$settings['AUTOMATIC_PAYOUTS']}else{'false'}
    $env:DEBUG='false';$env:LOGGING_LEVEL_ROOT='INFO';$env:LOGGING_LEVEL_ORG_SPRINGFRAMEWORK='INFO';$env:LOGGING_LEVEL_ORG_HIBERNATE='WARN'
    if(-not $Maven){$command=Get-Command mvn.cmd -ErrorAction SilentlyContinue;if($command){$Maven=$command.Source}else{$Maven=(Get-ChildItem (Join-Path $env:USERPROFILE '.m2\wrapper\dists') -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName}}
    Push-Location $repo;try{& $Maven "-Dmaven.repo.local=$(Join-Path $workspace '.maven-repository')" -pl payment-service spring-boot:run}finally{Pop-Location}
  }
}
