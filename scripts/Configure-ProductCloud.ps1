param(
    [Parameter(Mandatory=$true)][string]$ConnectionFile,
    [switch]$Apply
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$settings=@{}
foreach($line in Get-Content -LiteralPath $ConnectionFile){if($line -match '^DATABASE_URL=(.*)$'){$settings.DATABASE_URL=$matches[1].Trim().Trim('"').Trim("'")}}
if(-not $settings.DATABASE_URL){throw 'Set DATABASE_URL in the local connection file first.'}
try{$uri=[Uri]$settings.DATABASE_URL}catch{throw 'Invalid PostgreSQL connection string.'}
if($uri.Scheme -notin @('postgres','postgresql') -or -not $uri.Host -or -not $uri.UserInfo -or $uri.AbsolutePath -eq '/'){throw 'Expected a PostgreSQL connection string including database and credentials.'}
if($uri.Host -match '-pooler\.') {throw 'Use the direct connection for initial migration.'}
$credentials=$uri.UserInfo.Split(':',2)
if($credentials.Length -ne 2){throw 'Connection string must include a password.'}
$port=if($uri.Port -gt 0){$uri.Port}else{5432}
$database=[Uri]::UnescapeDataString($uri.AbsolutePath.TrimStart('/'))
$url="jdbc:postgresql://$($uri.Host):$port/$database`?sslmode=require"
Write-Output ('Target Product database: '+$url)
if(-not $Apply){Write-Output 'Preview only. Apply after the exported catalog has been restored and verified.';exit 0}
$target=Join-Path $repo 'product-service\.env'
$backup=Join-Path $repo '.local\product-cloud'
New-Item -ItemType Directory -Path $backup -Force|Out-Null
$content=if(Test-Path -LiteralPath $target){[IO.File]::ReadAllText($target)}else{''}
if(Test-Path -LiteralPath $target){Copy-Item -LiteralPath $target -Destination (Join-Path $backup ('product-env-before-'+(Get-Date -Format 'yyyyMMdd-HHmmssfff')+'.txt'))}
$values=@{DB_URL=$url;DB_USERNAME=[Uri]::UnescapeDataString($credentials[0]);DB_PASSWORD=[Uri]::UnescapeDataString($credentials[1]);JPA_DDL_AUTO='validate';SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='5'}
foreach($key in $values.Keys){
    $pattern='(?m)^'+[regex]::Escape($key)+'=.*$'
    $replacement=$key+'='+$values[$key]
    if([regex]::IsMatch($content,$pattern)){$content=[regex]::Replace($content,$pattern,[System.Text.RegularExpressions.MatchEvaluator]{param($m)$replacement})}
    else{$content=$content.TrimEnd()+[Environment]::NewLine+$replacement+[Environment]::NewLine}
}
[IO.File]::WriteAllText($target,$content,[Text.UTF8Encoding]::new($false))
Write-Output 'Cloud Product database configured. Restart product-service.'
