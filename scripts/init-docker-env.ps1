param(
    [ValidateSet('dev','prod')][string]$Mode='dev',
    [ValidateRange(1,65535)][int]$Port=8088
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$envPath=Join-Path $projectRoot '.env'
if(Test-Path -LiteralPath $envPath){
    Write-Output '.env already exists; existing passwords and encryption keys were preserved. Edit it manually if needed.'
    return
}
function New-RandomBytes([int]$Length){
    $bytes=New-Object byte[] $Length
    $random=[System.Security.Cryptography.RandomNumberGenerator]::Create()
    try{$random.GetBytes($bytes)}finally{$random.Dispose()}
    return ,$bytes
}
function New-RandomHex([int]$Length){
    return ([System.BitConverter]::ToString((New-RandomBytes $Length))).Replace('-','').ToLowerInvariant()
}
$values=@{
    'replace-with-a-strong-database-password'=(New-RandomHex 24)
    'replace-with-another-strong-password'=(New-RandomHex 24)
    'replace-with-a-strong-redis-password'=(New-RandomHex 24)
    'base64-of-32-random-bytes'=[System.Convert]::ToBase64String((New-RandomBytes 32))
    'at-least-32-random-characters-separate-from-encryption-key'=(New-RandomHex 32)
    'replace-with-12-to-64-characters-with-letters-and-digits'=('Admin9-'+(New-RandomHex 24))
}
$content=[System.IO.File]::ReadAllText((Join-Path $projectRoot '.env.example'))
if($content -match '(?m)^DB_USERNAME=root\r?$'){
    $values['replace-with-another-strong-password']=$values['replace-with-a-strong-database-password']
}
foreach($placeholder in $values.Keys){$content=$content.Replace($placeholder,$values[$placeholder])}
$content=$content.Replace('APP_MODE=dev',"APP_MODE=$Mode").Replace('WEB_PORT=8088',"WEB_PORT=$Port")
# CreateNew prevents overwriting a configuration created by another process.
$stream=[System.IO.File]::Open($envPath,[System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write)
try{
    $bytes=[System.Text.UTF8Encoding]::new($false).GetBytes($content)
    $stream.Write($bytes,0,$bytes.Length)
}finally{$stream.Dispose()}
Write-Output "Created .env with fresh passwords and keys (mode=$Mode, port=$Port)."
Write-Output 'Keep this file with your database backups; encryption keys are needed to read existing account and resume data.'
