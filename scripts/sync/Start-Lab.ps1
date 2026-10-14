param([string]$LabDirectory=(Join-Path $PSScriptRoot '../../tmp/sync-lab'),[string]$MySqlBin='C:/Program Files/MySQL/MySQL Server 8.0/bin',[string]$JavaExe,[string]$Jar)
$ErrorActionPreference='Stop'
$LabDirectory=(Resolve-Path -LiteralPath $LabDirectory).Path
$config=Get-Content -LiteralPath (Join-Path $LabDirectory 'node-a.local.json') -Raw | ConvertFrom-Json
$uri=[uri]($config.DB_URL -replace '^jdbc:','')
if($uri.Host -ne '127.0.0.1' -or $uri.Port -eq 3306 -or $uri.AbsolutePath -notmatch '^/attendance_sync_test_[a-z0-9_]+$'){throw 'Only an isolated fictional lab can be restarted'}
$dataDirectory=(Resolve-Path -LiteralPath (Join-Path $LabDirectory 'mysql-data')).Path
if(!(Test-Path -LiteralPath (Join-Path $dataDirectory 'mysql'))){throw 'Initialized test data directory required; this script never initializes/rebuilds a database'}
$mysql=(Resolve-Path -LiteralPath (Join-Path $MySqlBin 'mysql.exe')).Path
$mysqld=(Resolve-Path -LiteralPath (Join-Path $MySqlBin 'mysqld.exe')).Path
$listener=Get-NetTCPConnection -LocalPort $uri.Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if(!$listener) {
    $process=Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults',('--basedir="'+[IO.Path]::GetFullPath((Join-Path $MySqlBin '..'))+'"'),('--datadir="'+$dataDirectory+'"'),"--port=$($uri.Port)",'--bind-address=127.0.0.1','--mysqlx=OFF','--default-time-zone=+08:00',('--log-error="'+(Join-Path $LabDirectory 'mysql-server.log')+'"')) -WindowStyle Hidden -PassThru
    $processId=$process.Id
} else {$processId=$listener.OwningProcess}
$savedPassword=[Environment]::GetEnvironmentVariable('MYSQL_PWD','Process')
$ready=$false
try {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD',$null,'Process')
    for($i=0;$i -lt 30;$i++) {
        $actual=& $mysql --protocol=TCP --host=127.0.0.1 "--port=$($uri.Port)" --user=root --connect-timeout=1 --batch --skip-column-names --execute='SELECT @@datadir' 2> (Join-Path $LabDirectory 'mysql-ready.log')
        if(!$LASTEXITCODE){
            if([IO.Path]::GetFullPath($actual.Trim()).TrimEnd('\','/') -ne $dataDirectory.TrimEnd('\','/')){throw 'Listening MySQL does not belong to this lab'}
            $ready=$true;break
        }
        Start-Sleep -Milliseconds 300
    }
} finally {[Environment]::SetEnvironmentVariable('MYSQL_PWD',$savedPassword,'Process')}
if(!$ready){throw 'Test MySQL did not become ready'}
$active=Get-NetTCPConnection -LocalPort $uri.Port -State Listen | Select-Object -First 1
@{processId=$active.OwningProcess;executable=$mysqld;dataDirectory=$dataDirectory;port=$uri.Port;mysqlBin=$MySqlBin} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $LabDirectory 'mysql.local.json') -Encoding utf8
& (Join-Path $PSScriptRoot 'Start-ThreeNodes.ps1') -ConfigDirectory $LabDirectory -JavaExe $JavaExe -Jar $Jar
