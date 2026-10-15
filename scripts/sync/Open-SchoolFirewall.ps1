$ErrorActionPreference='Stop'
$ip='10.32.54.192'
$port=8081
if(!(Get-NetIPAddress -AddressFamily IPv4 -IPAddress $ip -ErrorAction SilentlyContinue)){throw "This computer does not have school IP $ip"}
# Run explicitly as administrator on the matching school computer. Never open MySQL.
foreach($rule in @(@{Name='Attendance-LAN-Web-A';Port=5173;Remote='LocalSubnet'},@{Name='Attendance-LAN-Sync-A';Port=$port;Remote=@('10.32.54.192','10.32.54.193','10.32.54.172')})){
    if(Get-NetFirewallRule -Name $rule.Name -ErrorAction SilentlyContinue){Write-Output "$($rule.Name) already exists; inspect it before changing.";continue}
    New-NetFirewallRule -Name $rule.Name -DisplayName $rule.Name -Direction Inbound -Action Allow -Protocol TCP -LocalAddress $ip -LocalPort $rule.Port -RemoteAddress $rule.Remote -Profile Any | Out-Null
}
Write-Output 'School LAN rules configured. Web: local subnet; backend: three known nodes only. No database port opened.'
