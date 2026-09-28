param(
    [ValidateSet('start','test','reload','stop')][string]$Action='start',
    [string]$NginxExe,
    [switch]$SkipBuild
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$nginxRoot=Join-Path $projectRoot 'deploy/nginx'
if(!$NginxExe){
    $installed=Get-Command nginx.exe -ErrorAction SilentlyContinue
    if($installed){$NginxExe=$installed.Source}
    elseif(Test-Path 'D:/phpstudy_pro/Extensions/Nginx1.15.11/nginx.exe'){$NginxExe='D:/phpstudy_pro/Extensions/Nginx1.15.11/nginx.exe'}
    else{throw 'Pass -NginxExe with the full path to your installed nginx.exe.'}
}
$NginxExe=(Resolve-Path -LiteralPath $NginxExe).Path
$prefix=$nginxRoot.Replace('\','/')+'/'
foreach($folder in @('logs','temp/client','temp/proxy')){New-Item -ItemType Directory -Force (Join-Path $nginxRoot $folder) | Out-Null}
$mimeFile=Join-Path (Split-Path $NginxExe -Parent) 'conf/mime.types'
if(!(Test-Path -LiteralPath $mimeFile)){throw 'Cannot find conf/mime.types beside the installed nginx.exe.'}
Copy-Item -LiteralPath $mimeFile -Destination (Join-Path $nginxRoot 'runtime-mime.types') -Force
if($Action -eq 'stop'){
    & $NginxExe -p $prefix -c nginx.conf -s quit
    if($LASTEXITCODE -ne 0){throw 'Nginx stop failed. Check deploy/nginx/logs/error.log.'}
    exit 0
}
if(!$SkipBuild -and $Action -in @('start','reload')){
    Push-Location (Join-Path $projectRoot 'frontend')
    try{
        if(!(Test-Path node_modules)){& npm.cmd ci;if($LASTEXITCODE -ne 0){throw 'npm ci failed'}}
        & npm.cmd run build
        if($LASTEXITCODE -ne 0){throw 'Frontend build failed; Nginx was not started or reloaded.'}
    }finally{Pop-Location}
}
if(!(Test-Path (Join-Path $projectRoot 'frontend/dist/index.html'))){throw 'Build frontend first: cd frontend; npm run build'}
& $NginxExe -p $prefix -c nginx.conf -t
if($LASTEXITCODE -ne 0){throw 'Nginx configuration validation failed'}
if($Action -eq 'test'){exit 0}
if($Action -eq 'reload'){
    & $NginxExe -p $prefix -c nginx.conf -s reload
    if($LASTEXITCODE -ne 0){throw 'Nginx reload failed'}
}else{
    $pidFile=Join-Path $nginxRoot 'logs/campus-nginx.pid'
    if(Test-Path $pidFile){
        $masterId=(Get-Content $pidFile -Raw) -replace '\s', ''
        if($masterId -match '^\d+$' -and (Get-Process -Id ([int]$masterId) -ErrorAction SilentlyContinue)){
            throw 'The project Nginx is already running. Use -Action reload or stop.'
        }
    }
    Start-Process -FilePath $NginxExe -ArgumentList @('-p',('"'+$prefix+'"'),'-c','nginx.conf') -WorkingDirectory $nginxRoot -WindowStyle Hidden
    $started=$false
    for($attempt=0;$attempt -lt 20;$attempt++){
        Start-Sleep -Milliseconds 200
        if(Test-Path $pidFile){
            $masterId=(Get-Content $pidFile -Raw) -replace '\s', ''
            if($masterId -match '^\d+$' -and (Get-Process -Id ([int]$masterId) -ErrorAction SilentlyContinue)){$started=$true;break}
        }
    }
    if(!$started){throw 'Nginx did not start. Check deploy/nginx/logs/error.log and port 8088.'}
}
Write-Output 'Frontend: http://localhost:8088/publisher | /student | /admin'
Write-Output 'Start the normal Spring Boot backend on port 8080 in IDEA.'


