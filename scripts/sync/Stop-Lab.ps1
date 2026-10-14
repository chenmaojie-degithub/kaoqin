param([string]$LabDirectory=(Join-Path $PSScriptRoot '../../tmp/sync-lab'))
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'Stop-Nodes.ps1') -ConfigDirectory $LabDirectory
$stateFile=Join-Path $LabDirectory 'mysql.local.json'
if(!(Test-Path -LiteralPath $stateFile)){return}
$state=Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
$process=Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$state.processId)"
if($process){
    if($process.ExecutablePath -ne $state.executable -or $process.CommandLine -notmatch [regex]::Escape($state.dataDirectory) -or $state.port -eq 3306){throw 'Test MySQL identity mismatch; refusing shutdown'}
    $savedPassword=[Environment]::GetEnvironmentVariable('MYSQL_PWD','Process')
    try {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD',$null,'Process')
        & (Join-Path $state.mysqlBin 'mysqladmin.exe') --protocol=TCP --host=127.0.0.1 "--port=$($state.port)" --user=root shutdown
        if($LASTEXITCODE){throw 'Graceful test MySQL shutdown failed'}
    } finally {[Environment]::SetEnvironmentVariable('MYSQL_PWD',$savedPassword,'Process')}
}
Remove-Item -LiteralPath $stateFile
Write-Output 'Test MySQL stopped; all test databases retained on disk'
