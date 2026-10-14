param([string]$ConfigDirectory=(Join-Path $PSScriptRoot '../../tmp/sync-lab'))
$ErrorActionPreference='Stop'
foreach($file in Get-ChildItem -LiteralPath $ConfigDirectory -Filter '*.process.local.json') {
    $state=Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
    $process=Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$state.processId)"
    if($process) {
        if($process.ExecutablePath -ne $state.java -or $process.CommandLine -notmatch [regex]::Escape($state.jar) -or $process.CommandLine -notmatch [regex]::Escape("--sync.node-id=$($state.nodeId)")){throw 'PID identity mismatch; refusing to stop an unrelated process'}
        Stop-Process -Id ([int]$state.processId)
    }
    Remove-Item -LiteralPath $file.FullName
    Write-Output "$($state.nodeId) stopped"
}
