$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
if ($mavenCommand) { $mavenPath = $mavenCommand.Source }
elseif (Test-Path 'D:\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd') {
    $mavenPath = 'D:\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd'
} else { throw 'Please install Maven 3.6.3+ and add mvn to PATH.' }
Set-Location $projectRoot
& $mavenPath -pl backend spring-boot:run '-Dspring-boot.run.profiles=dev'
