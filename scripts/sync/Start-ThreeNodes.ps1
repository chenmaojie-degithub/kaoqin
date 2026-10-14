param([string]$ConfigDirectory=(Join-Path $PSScriptRoot '../../tmp/sync-lab'),[string]$JavaExe,[string]$Jar)
$ErrorActionPreference='Stop'
foreach($name in @('node-a.local.json','node-b.local.json','node-c.local.json')) {
    & (Join-Path $PSScriptRoot 'Start-Node.ps1') -ConfigFile (Join-Path $ConfigDirectory $name) -JavaExe $JavaExe -Jar $Jar
}
