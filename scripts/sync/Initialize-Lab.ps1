param(
    [string]$LabDirectory=(Join-Path $PSScriptRoot '../../tmp/sync-lab'),
    [int]$MySqlPort=3307,
    [string]$MySqlBin='C:/Program Files/MySQL/MySQL Server 8.0/bin',
    [string]$JavaExe
)
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$LabDirectory=[IO.Path]::GetFullPath($LabDirectory)
if($MySqlPort -eq 3306 -or $MySqlPort -lt 1024 -or $MySqlPort -gt 65535){throw 'Use a separate test MySQL port, never 3306'}
if(Test-Path -LiteralPath $LabDirectory){throw 'Lab directory already exists. Reuse its configuration or choose a new directory; this script never deletes/rebuilds databases.'}
if(Get-NetTCPConnection -LocalPort $MySqlPort -State Listen -ErrorAction SilentlyContinue){throw 'MySQL test port already in use'}
if(!$JavaExe){$available=Get-Command java -ErrorAction SilentlyContinue;$JavaExe=if($available){$available.Source}else{Join-Path $repoRoot 'tmp/tools/jdk-17.0.20.1+1/bin/java.exe'}}
$JavaExe=(Resolve-Path -LiteralPath $JavaExe).Path
$mysqld=(Resolve-Path -LiteralPath (Join-Path $MySqlBin 'mysqld.exe')).Path
$mysql=(Resolve-Path -LiteralPath (Join-Path $MySqlBin 'mysql.exe')).Path
New-Item -ItemType Directory -Path $LabDirectory | Out-Null
$dataDirectory=Join-Path $LabDirectory 'mysql-data'
New-Item -ItemType Directory -Path $dataDirectory | Out-Null
& $mysqld --no-defaults --initialize-insecure "--basedir=$([IO.Path]::GetFullPath((Join-Path $MySqlBin '..')))" "--datadir=$dataDirectory" --console *> (Join-Path $LabDirectory 'mysql-initialize.log')
if($LASTEXITCODE){throw 'Test MySQL initialization failed; inspect the local log'}
$mysqlProcess=Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults',('--basedir="'+[IO.Path]::GetFullPath((Join-Path $MySqlBin '..'))+'"'),('--datadir="'+$dataDirectory+'"'),"--port=$MySqlPort",'--bind-address=127.0.0.1','--mysqlx=OFF','--default-time-zone=+08:00',('--log-error="'+(Join-Path $LabDirectory 'mysql-server.log')+'"')) -WindowStyle Hidden -PassThru
@{processId=$mysqlProcess.Id;executable=$mysqld;dataDirectory=$dataDirectory;port=$MySqlPort;mysqlBin=$MySqlBin} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $LabDirectory 'mysql.local.json') -Encoding utf8
$ready=$false
for($i=0;$i -lt 30;$i++) {
    & $mysql --protocol=TCP --host=127.0.0.1 "--port=$MySqlPort" --user=root --connect-timeout=1 --execute='SELECT 1' *> (Join-Path $LabDirectory 'mysql-ready.log')
    if(!$LASTEXITCODE){$ready=$true;break};Start-Sleep -Milliseconds 300
}
if(!$ready){throw 'Test MySQL did not become ready; inspect local logs'}
function New-LabSecret { $bytes=New-Object byte[] 48;[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes);[Convert]::ToBase64String($bytes) }
$keys=@()
for($i=0;$i -lt 3;$i++) {
    $encoded=& $JavaExe (Join-Path $PSScriptRoot 'GenerateKeys.java') 2> (Join-Path $LabDirectory 'keys.err.log')
    if($LASTEXITCODE){throw 'Java 17 key generation failed'}
    $keys+=($encoded | ConvertFrom-Json)
}
$schema=[IO.File]::ReadAllText((Join-Path $repoRoot 'database/schema.sql'))
$schema=$schema.Substring($schema.IndexOf('CREATE TABLE `user`'))
if($schema -match '(?im)^\s*(DROP|DELETE|TRUNCATE|USE|CREATE DATABASE)\b'){throw 'Unexpected destructive statement in extracted test schema'}
$migration=[IO.File]::ReadAllText((Join-Path $repoRoot 'database/migrate_sync.sql'))
$fixture=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'fixture.sql'))
$savedPassword=[Environment]::GetEnvironmentVariable('MYSQL_PWD','Process')
$savedEncoding=$OutputEncoding
try {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD',$null,'Process')
    $OutputEncoding=[Text.UTF8Encoding]::new($false)
    for($i=0;$i -lt 3;$i++) {
        $letter=[char](97+$i);$database="attendance_sync_test_$letter";$account="sync_test_$letter";$dbPassword=New-LabSecret
        $script="CREATE DATABASE $database CHARACTER SET utf8mb4;`nUSE $database;`n$schema`n$migration`n$fixture`nCREATE USER '$account'@'localhost' IDENTIFIED BY '$dbPassword';`nGRANT ALL PRIVILEGES ON $database.* TO '$account'@'localhost';"
        $script | & $mysql --protocol=TCP --host=127.0.0.1 "--port=$MySqlPort" --user=root --default-character-set=utf8mb4 *> (Join-Path $LabDirectory "bootstrap-$letter.log")
        if($LASTEXITCODE){throw "Test database initialization failed for node $letter; inspect local log"}
        $peers=@()
        for($j=0;$j -lt 3;$j++){if($i -ne $j){$peers+=@{nodeId="NODE_$([char](65+$j))";url="http://127.0.0.1:$((8081+$j))/api";token=$keys[$j].token;publicKey=$keys[$j].publicKey}}}
        $config=@{SERVER_PORT=[string](8081+$i);DB_URL="jdbc:mysql://127.0.0.1:$MySqlPort/$($database)?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";DB_USERNAME=$account;DB_PASSWORD=$dbPassword;JWT_SECRET=(New-LabSecret);SYNC_ENABLED='true';SYNC_NODE_ID="NODE_$([char](65+$i))";SYNC_SCHOOL_ID='TEST_SCHOOL';SYNC_TOKEN=$keys[$i].token;SYNC_PRIVATE_KEY=$keys[$i].privateKey;SYNC_PUBLIC_KEY=$keys[$i].publicKey;SYNC_PEERS_JSON=(ConvertTo-Json -InputObject $peers -Compress);SYNC_INTERVAL_MS='5000';SYNC_TIMEOUT_MS='2000';SYNC_MAX_ATTEMPTS='2'}
        $config | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $LabDirectory "node-$letter.local.json") -Encoding utf8
    }
} finally {[Environment]::SetEnvironmentVariable('MYSQL_PWD',$savedPassword,'Process');$OutputEncoding=$savedEncoding}
Write-Output "Isolated test MySQL ready on 127.0.0.1:$MySqlPort; three fictional databases and local node configs are in $LabDirectory"
