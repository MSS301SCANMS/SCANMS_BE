param([string]$IdeaConfigDir = (Join-Path $env:APPDATA 'JetBrains/IntelliJIdea2025.1'))
$ErrorActionPreference='Stop'
if(Get-Process idea64 -ErrorAction SilentlyContinue) { throw 'Close IntelliJ first so its settings are not overwritten while installing.' }
$repo=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $repo '.local/intellij'
[void][IO.Directory]::CreateDirectory($runtime)
$toolsDir=Join-Path $IdeaConfigDir 'tools'
[void][IO.Directory]::CreateDirectory($toolsDir)
$toolFile=Join-Path $toolsDir 'SCANMS.xml'
if(Test-Path -LiteralPath $toolFile) { Copy-Item -LiteralPath $toolFile -Destination (Join-Path $runtime 'tools-before.xml') -Force }
$toolDoc=New-Object System.Xml.XmlDocument
$toolDoc.LoadXml('<toolSet name="SCANMS"><tool name="Prepare local services" description="Start and wait for databases, Redis, Kafka and Keycloak before launching Spring services" showInMainMenu="true" showInEditor="false" showInProject="false" showInSearchPopup="true" disabled="false" useConsole="true" showConsoleOnStdOut="false" showConsoleOnStdErr="true" synchronizeAfterRun="true"><exec /></tool></toolSet>')
$exec=$toolDoc.SelectSingleNode('//exec')
foreach($entry in @(@('COMMAND',(Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe')),@('PARAMETERS','-NoProfile -ExecutionPolicy Bypass -File "$ProjectFileDir$/scripts/Prepare-IntelliJ.ps1"'),@('WORKING_DIRECTORY','$ProjectFileDir$'))) {
    $node=$toolDoc.CreateElement('option'); $node.SetAttribute('name',$entry[0]); $node.SetAttribute('value',$entry[1]); [void]$exec.AppendChild($node)
}
$toolDoc.Save($toolFile)
$workspace=Join-Path $repo '.idea/workspace.xml'
Copy-Item -LiteralPath $workspace -Destination (Join-Path $runtime 'workspace-before-startup.xml') -Force
$doc=New-Object System.Xml.XmlDocument
$doc.PreserveWhitespace=$true; $doc.Load($workspace)
$names=@{UserServiceApplication='user-service';ProductServiceApplication='product-service';OrderServiceApplication='order-service';PaymentServiceApplication='payment-service';PromotionAffiliateServiceApplication='promotion-affiliate-service';ScanmsGatewayApplication='scanms-gateway'}
foreach($name in $names.Keys) {
    $config=$doc.SelectSingleNode("//component[@name='RunManager']/configuration[@name='$name']")
    if(-not $config) { throw "Run configuration missing: $name" }
    $extension=$config.SelectSingleNode("extension[@name='net.ashald.envfile']")
    if(-not $extension) { throw "Enable/install the EnvFile plugin before configuring $name" }
    foreach($setting in @('IS_ENABLED','IS_PATH_MACRO_SUPPORTED')) { $node=$extension.SelectSingleNode("option[@name='$setting']"); if($node){$node.SetAttribute('value','true')} }
    $entries=$extension.SelectSingleNode('ENTRIES')
    foreach($entry in @($entries.SelectNodes('ENTRY'))) { if($entry.GetAttribute('PARSER') -eq 'env') { [void]$entries.RemoveChild($entry) } }
    $entry=$doc.CreateElement('ENTRY')
    foreach($pair in @{IS_ENABLED='true';PARSER='env';IS_EXECUTABLE='false';PATH=('$PROJECT_DIR$/.local/intellij/'+$names[$name]+'.env')}.GetEnumerator()) {$entry.SetAttribute($pair.Key,$pair.Value)}
    [void]$entries.AppendChild($entry)
    $method=$config.SelectSingleNode('method')
    if(-not $method) {$method=$doc.CreateElement('method');$method.SetAttribute('v','2');[void]$config.AppendChild($method)}
    foreach($old in @($method.SelectNodes("option[@name='ToolBeforeRunTask']"))) {if($old.GetAttribute('actionId') -eq 'Tool_SCANMS_Prepare local services'){[void]$method.RemoveChild($old)}}
    $task=$doc.CreateElement('option');$task.SetAttribute('name','ToolBeforeRunTask');$task.SetAttribute('enabled','true');$task.SetAttribute('actionId','Tool_SCANMS_Prepare local services')
    [void]$method.PrependChild($task)
    Write-Output "${name}: automatic dependency preparation enabled"
}
$doc.Save($workspace)
Write-Output 'Installed. Open IntelliJ, select Spring Boot and Run; dependencies are prepared before each service.'
