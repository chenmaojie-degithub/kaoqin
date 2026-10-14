param([string]$ConfigDirectory=(Join-Path $PSScriptRoot '../../tmp/sync-lab'))
$ErrorActionPreference='Stop'
$nodes=@()
foreach($name in @('node-a.local.json','node-b.local.json','node-c.local.json')) {
    $config=Get-Content -LiteralPath (Join-Path $ConfigDirectory $name) -Raw | ConvertFrom-Json
    if($config.DB_URL -notmatch '^jdbc:mysql://127\.0\.0\.1:(?!3306/)[0-9]+/attendance_sync_test_[a-z0-9_]+\?'){throw 'This smoke test only writes to isolated loopback test databases'}
    $nodes+=@{id=$config.SYNC_NODE_ID;url="http://127.0.0.1:$($config.SERVER_PORT)/api"}
}
function Invoke-LabApi($node,$method,$path,$token,$body) {
    $args=@{Uri=($node.url+$path);Method=$method;TimeoutSec=30;ContentType='application/json'}
    if($token){$args.Headers=@{Authorization="Bearer $token"}}
    if($null -ne $body){$args.Body=ConvertTo-Json -InputObject $body -Depth 8 -Compress}
    $response=Invoke-RestMethod @args
    if($response.code -ne 0){throw 'Test API returned a business error'}
    return $response.data
}
for($i=0;$i -lt 3;$i++) {
    $node=$nodes[$i];$ready=$false
    for($attempt=0;$attempt -lt 60;$attempt++) {
        try {$admin=Invoke-LabApi $node POST '/auth/login' $null @{username='sync_admin';password='SyncTest!2026'};$ready=$true;break}catch{Start-Sleep -Milliseconds 300}
    }
    if(!$ready){throw "$($node.id) not ready; inspect its local logs"}
    $node.admin=$admin.token
    $teacher=Invoke-LabApi $node POST '/auth/login' $null @{username='sync_teacher';password='SyncTest!2026'}
    $student=Invoke-LabApi $node POST '/auth/login' $null @{username='sync_student';password='SyncTest!2026'}
    $node.student=$student.token
    $teaching=Invoke-LabApi $node GET '/teaching' $teacher.token $null
    $courseName="Fictional Course $([char](65+$i))"
    $cc=@($teaching | Where-Object courseName -eq $courseName)[0]
    if(!$cc){throw 'Fictional teaching assignment missing'}
    $date=Get-Date -Format 'yyyy-MM-dd'
    $tasks=@(Invoke-LabApi $node GET "/attendance/tasks?courseClassId=$($cc.id)&date=$date" $teacher.token $null)
    $task=if($tasks.Count){$tasks[0]}else{Invoke-LabApi $node POST '/attendance/tasks' $teacher.token @{courseClassId=$cc.id;attendanceDate=$date}}
    if($i -eq 0) {
        $today=@(Invoke-LabApi $node GET '/student-portal/today-check-ins' $student.token $null)
        if(!($today | Where-Object {$_.taskId -eq $task.id -and $_.checkedIn})) {
            $session=Invoke-LabApi $node POST '/attendance/sessions' $teacher.token @{taskId=$task.id}
            Invoke-LabApi $node POST '/attendance/check-in' $student.token @{sessionId=$session.sessionId;signCode=$session.signCode} | Out-Null
        }
        Write-Output "$($node.id): local password validation completed; exporting only the common attendance result"
    } else {
        $roster=@(Invoke-LabApi $node GET "/attendance/tasks/$($task.id)/students" $teacher.token $null)
        Invoke-LabApi $node PUT "/attendance/tasks/$($task.id)/records" $teacher.token @(@{studentId=$roster[0].studentId;status='ABSENT'}) | Out-Null
        Write-Output "$($node.id): ordinary attendance recorded; no password API used"
    }
}
$converged=$false
for($attempt=0;$attempt -lt 120;$attempt++) {
    $complete=0
    foreach($node in $nodes) {
        $rows=@(Invoke-LabApi $node GET '/student-portal/today-check-ins' $node.student $null)
        $localReceipts=if($node.id -eq 'NODE_A'){1}else{0}
        if($rows.Count -eq 3 -and @($rows | Where-Object checkedIn -eq $true).Count -eq $localReceipts){$complete++}
    }
    if($complete -eq 3){$converged=$true;break};Start-Sleep -Milliseconds 250
}
if(!$converged){throw 'Automatic sync did not converge within the test timeout'}
foreach($node in $nodes) {
    Invoke-LabApi $node POST '/sync/admin/push' $node.admin $null | Out-Null
    Invoke-LabApi $node POST '/sync/admin/pull' $node.admin $null | Out-Null
    $status=Invoke-LabApi $node GET '/sync/admin/status' $node.admin $null
    if($status.peers.directions.last_error | Where-Object {$_}){throw 'Manual synchronization reports a peer failure'}
    Write-Output "$($node.id): 3 common attendance results visible; auto sync and admin Push/Pull passed"
}
