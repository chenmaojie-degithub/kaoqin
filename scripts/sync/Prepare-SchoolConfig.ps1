param([string]$ConfigDirectory, [string]$JavaExe)
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if(!$ConfigDirectory){$ConfigDirectory=Join-Path $repoRoot 'tmp/school-lan'}
if(!$JavaExe){
    $installedJava=Get-Command java -ErrorAction SilentlyContinue
    $JavaExe=if($installedJava){$installedJava.Source}else{Join-Path $repoRoot 'tmp/tools/jdk-17.0.20.1+1/bin/java.exe'}
}
$configFile=Join-Path $ConfigDirectory 'node-a.local.json'
if(Test-Path $configFile){throw 'Existing NODE_A configuration found. Refusing to replace keys or configuration.'}
$keyJson=& $JavaExe (Join-Path $PSScriptRoot 'GenerateKeys.java')
if($LASTEXITCODE -ne 0){throw 'Java 17 key generation failed'}
$keys=$keyJson | ConvertFrom-Json
New-Item -ItemType Directory -Force -Path $ConfigDirectory | Out-Null
$config=Get-Content -Raw (Join-Path $PSScriptRoot 'node-a.example.json') | ConvertFrom-Json
$config.SYNC_TOKEN=$keys.token
$config.SYNC_PRIVATE_KEY=$keys.privateKey
$config.SYNC_PUBLIC_KEY=$keys.publicKey
$jwtBytes=New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)
$config.JWT_SECRET=[Convert]::ToBase64String($jwtBytes)
# Start the website first; enable sync after reviewing migrations and exchanging real peer credentials.
$config.SYNC_ENABLED='false'
$config.SYNC_PEERS_JSON='[]'
$config | ConvertTo-Json -Depth 5 | Set-Content $configFile -Encoding utf8
Write-Output "Created only NODE_A private configuration: $configFile. Fill local database settings. Sync is initially disabled; configure real peers and apply reviewed incremental SQL before enabling it. No other node keys or configurations were generated."
