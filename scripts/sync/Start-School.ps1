param([string]$ConfigDirectory, [string]$JavaExe)
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if(!$ConfigDirectory){$ConfigDirectory=Join-Path $repoRoot 'tmp/school-lan'}
$configFile=Join-Path $ConfigDirectory 'node-a.local.json'
if(!(Test-Path $configFile)){throw 'Run Prepare-SchoolConfig.ps1 once to prepare this system NODE_A.'}
$config=Get-Content -Raw $configFile | ConvertFrom-Json
if(($config | ConvertTo-Json -Depth 5) -match '<[^>]+>'){throw 'Fill local database settings and credentials; placeholders are not runnable.'}
if($config.SYNC_NODE_ID -ne 'NODE_A'){throw 'This school launcher only starts NODE_A.'}
$ip='10.32.54.192'
if(!(Get-NetIPAddress -AddressFamily IPv4 -IPAddress $ip -ErrorAction SilentlyContinue)){throw "This computer does not have school IP $ip. Check the network configuration before starting."}
# No automatic database migrations. Apply the reviewed incremental SQL beforehand.
& (Join-Path $PSScriptRoot 'Start-Node.ps1') -ConfigFile $configFile -JavaExe $JavaExe
$ready=$false
for($attempt=0;$attempt -lt 30;$attempt++){
    try {
        $response=Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($config.SERVER_PORT)/api/auth/me" -TimeoutSec 2
        $ready=$response.StatusCode -eq 200
    } catch {
        $ready=$_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401
    }
    if($ready){break}
    Start-Sleep -Seconds 1
}
if(!$ready){throw 'Backend not ready. Inspect the node logs; frontend was not started.'}
Write-Output "Open http://${ip}:5173 from any computer on this LAN. Keep this terminal open for the frontend."
& (Join-Path $PSScriptRoot 'Start-Web.ps1') -BackendPort ([int]$config.SERVER_PORT)
