param([ValidateRange(1,65535)][int]$BackendPort=8081)
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$previousTarget=$env:VITE_API_PROXY_TARGET
$previousBase=$env:VITE_API_BASE_URL
Push-Location (Join-Path $repoRoot 'frontend')
try {
    $env:VITE_API_PROXY_TARGET="http://127.0.0.1:$BackendPort"
    $env:VITE_API_BASE_URL='/api'
    if(!(Test-Path 'node_modules/.bin/vite.cmd')){
        & npm.cmd ci
        if($LASTEXITCODE -ne 0){throw 'npm ci failed; check Node.js/npm and internet connectivity.'}
    }
    & npm.cmd run dev
    if($LASTEXITCODE -ne 0){throw 'Frontend startup failed; check whether port 5173 is already occupied.'}
} finally {
    Pop-Location
    $env:VITE_API_PROXY_TARGET=$previousTarget
    $env:VITE_API_BASE_URL=$previousBase
}
