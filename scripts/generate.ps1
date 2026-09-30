param([string]$Name = "payment-service", [string]$Owner = "payments")
$src = Join-Path $PSScriptRoot "..\templates\java-spring-service"
$dst = Join-Path $PSScriptRoot "..\generated\$Name"
if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
Copy-Item -Recurse -Force $src $dst
$utf8 = New-Object System.Text.UTF8Encoding($false)
Get-ChildItem -Recurse -File -Force $dst | ForEach-Object {
  $c = [IO.File]::ReadAllText($_.FullName)
  $c = $c.Replace('${{values.name}}', $Name).Replace('${{values.owner}}', $Owner)
  [IO.File]::WriteAllText($_.FullName, $c, $utf8)
}
Write-Host "Generated $dst"