# Optional local Quick Tunnel. Called under Prepare-IntelliJ's startup lock.
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$workspace=Split-Path $repo -Parent
$runtime=Join-Path $repo '.local/intellij'
$settingsPath=Join-Path $runtime 'public-demo.json'
if(-not(Test-Path -LiteralPath $settingsPath)){return}
$settings=Get-Content -LiteralPath $settingsPath -Raw|ConvertFrom-Json
if(-not $settings.enabled){return}
$fe=Join-Path $workspace 'SCANMS_FE'
$exe=Join-Path $workspace '.tools/cloudflared.exe'
if(-not(Test-Path -LiteralPath $exe)){throw 'Public demo is enabled but cloudflared.exe is missing. Configure a tunnel or disable public-demo.json.'}
if(-not(Test-Path -LiteralPath (Join-Path $fe 'dist/index.html'))){throw 'Build the public frontend before enabling the public demo.'}
$container=(& docker inspect scanms-demo-web|ConvertFrom-Json)[0]
if(-not $container){throw 'Public web container scanms-demo-web is missing.'}
if(-not $container.State.Running){& docker start scanms-demo-web|Out-Null;if($LASTEXITCODE -ne 0){throw 'Could not start public web container'}}
$manifestPath=Join-Path $runtime 'public-tunnel.json'
$active=$null
if(Test-Path -LiteralPath $manifestPath){$active=Get-Content -LiteralPath $manifestPath -Raw|ConvertFrom-Json}
$running=$false
if($active){
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($active.pid)" -ErrorAction SilentlyContinue
    $running=$process -and $process.Name -eq 'cloudflared.exe' -and $process.ExecutablePath -eq $exe -and $process.CommandLine -like '*http://localhost:8088*'
}
if(-not $running){
    $stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
    $errLog=Join-Path $runtime "tunnel-$stamp.err.log"; $outLog=Join-Path $runtime "tunnel-$stamp.out.log"
    $process=Start-Process -FilePath $exe -ArgumentList @('tunnel','--url','http://localhost:8088','--no-autoupdate') -WindowStyle Hidden -PassThru -RedirectStandardError $errLog -RedirectStandardOutput $outLog
    $active=@{pid=$process.Id;errLog=$errLog;outLog=$outLog}
    $active|ConvertTo-Json|Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Write-Output '[SCANMS] Starting a new temporary public tunnel...'
}
$until=[DateTime]::UtcNow.AddSeconds(60)
$url=$null
while([DateTime]::UtcNow -lt $until){
    if(Test-Path -LiteralPath $active.errLog){
        $match=[regex]::Match((Get-Content -LiteralPath $active.errLog -Raw),'https://[a-z0-9-]+\.trycloudflare\.com')
        if($match.Success){$url=$match.Value;break}
    }
    Start-Sleep -Seconds 1
}
if(-not $url){throw 'No public tunnel URL was issued. Check the tunnel log and Internet connection.'}
$siteUrl=if($settings.siteUrl){$settings.siteUrl}else{'https://vercel-upload-20261107-1.vercel.app'}
$siteUri=[Uri]$siteUrl
if($siteUri.Scheme -ne 'https' -or $siteUri.AbsolutePath -ne '/' -or $siteUri.UserInfo -or $siteUri.Query -or $siteUri.Fragment){throw 'Public frontend URL must be an HTTPS origin without a path.'}
$siteUrl=$siteUri.GetLeftPart([UriPartial]::Authority)
@{enabled=$true;url=$url;siteUrl=$siteUrl;updatedAt=(Get-Date).ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath $settingsPath -Encoding UTF8
$config=@{publicUrl=$siteUrl;siteUrl=$siteUrl;backendUrl=$url}|ConvertTo-Json -Compress
foreach($path in @((Join-Path $fe 'public/demo-public-config.json'),(Join-Path $fe 'dist/demo-public-config.json'))){
    [IO.File]::WriteAllText($path,$config,(New-Object Text.UTF8Encoding($false)))
}
$oldRuntime=Join-Path $workspace '.run-logs/demo-public'
if(Test-Path -LiteralPath $oldRuntime){[IO.File]::WriteAllText((Join-Path $oldRuntime 'public-url.txt'),$url)}
Write-Output "[SCANMS] Temporary public website: $url"
Write-Output '[SCANMS] Keep this PC and tunnel running. Old links cannot be retained after a new URL is issued.'
