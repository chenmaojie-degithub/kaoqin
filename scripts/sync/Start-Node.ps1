param(
    [Parameter(Mandatory=$true)][string]$ConfigFile,
    [string]$JavaExe,
    [string]$Jar
)
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (!$JavaExe) {
    $installedJava=Get-Command java -ErrorAction SilentlyContinue
    $JavaExe=if($installedJava){$installedJava.Source}else{Join-Path $repoRoot 'tmp/tools/jdk-17.0.20.1+1/bin/java.exe'}
}
if (!$Jar) {$Jar=Join-Path $repoRoot 'backend/target/attendance-backend-1.0.0.jar'}
$JavaExe=(Resolve-Path -LiteralPath $JavaExe).Path
$Jar=(Resolve-Path -LiteralPath $Jar).Path
$ConfigFile=(Resolve-Path -LiteralPath $ConfigFile).Path
$nodeConfig=Get-Content -LiteralPath $ConfigFile -Raw | ConvertFrom-Json
foreach($required in @('SERVER_PORT','DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET','SYNC_NODE_ID','SYNC_SCHOOL_ID','SYNC_TOKEN','SYNC_PRIVATE_KEY','SYNC_PUBLIC_KEY','SYNC_PEERS_JSON')) {
    if(!$nodeConfig.PSObject.Properties[$required]) {throw "Missing configuration field: $required"}
}
$nodeId=[string]$nodeConfig.SYNC_NODE_ID
if($nodeId -notmatch '^[A-Za-z0-9_-]{1,64}$'){throw 'Invalid node ID'}
$listenPort=[int]$nodeConfig.SERVER_PORT
if(Get-NetTCPConnection -LocalPort $listenPort -State Listen -ErrorAction SilentlyContinue){throw "Port $listenPort already in use"}
$configDirectory=Split-Path -Parent $ConfigFile
$stateFile=Join-Path $configDirectory "$nodeId.process.local.json"
if(Test-Path -LiteralPath $stateFile){throw 'Process state already exists; run Stop-Nodes.ps1 or inspect the stale state before restarting'}
$previousEnvironment=@{}
try {
    foreach($property in $nodeConfig.PSObject.Properties) {
        if($property.Name -notmatch '^(SERVER_PORT|SERVER_ADDRESS|CORS_ALLOWED_ORIGINS|DB_URL|DB_USERNAME|DB_PASSWORD|JWT_SECRET|SYNC_[A-Z_]+)$'){throw 'Unexpected environment key'}
        $previousEnvironment[$property.Name]=[Environment]::GetEnvironmentVariable($property.Name,'Process')
        [Environment]::SetEnvironmentVariable($property.Name,[string]$property.Value,'Process')
    }
    $process=Start-Process -FilePath $JavaExe -ArgumentList @('-Xms64m','-Xmx256m','-jar',('"'+$Jar+'"'),'--spring.config.import=optional:classpath:/no-shared-local.properties',"--sync.node-id=$nodeId") -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $configDirectory "$nodeId.out.log") -RedirectStandardError (Join-Path $configDirectory "$nodeId.err.log")
    @{nodeId=$nodeId;processId=$process.Id;jar=$Jar;java=$JavaExe;port=$listenPort} | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding utf8
    Write-Output "$nodeId starting: port=$listenPort pid=$($process.Id); logs in $configDirectory"
} finally {
    foreach($key in $previousEnvironment.Keys){[Environment]::SetEnvironmentVariable($key,$previousEnvironment[$key],'Process')}
}
