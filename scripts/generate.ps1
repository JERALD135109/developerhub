param(
  [ValidatePattern('^[a-z][a-z0-9-]{2,40}$')][string]$Name = 'payment-service',
  [ValidatePattern('^[a-z][a-z0-9-]{1,30}$')][string]$Owner = 'payments',
  [ValidateSet('dev','staging','prod')][string]$Environment = 'dev',
  [ValidateSet('None','PostgreSQL')][string]$Database = 'None',
  [ValidatePattern('^[a-zA-Z0-9-]+$')][string]$RepoOwner = 'developer',
  [string]$Domain = 'apps.localhost'
)
$sourcePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../templates/java-spring-service'))
$outputRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../generated'))
$outputPath = [IO.Path]::GetFullPath((Join-Path $outputRoot $Name))
if (-not $outputPath.StartsWith($outputRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Output path escaped generated directory' }
if (Test-Path -LiteralPath $outputPath) { throw 'Output already exists; choose another service name or review it before removing it.' }
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
Copy-Item -LiteralPath $sourcePath -Destination $outputPath -Recurse
$encoding = New-Object System.Text.UTF8Encoding($false)
Get-ChildItem -LiteralPath $outputPath -Recurse -File -Force | Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' } | ForEach-Object {
  $content = [IO.File]::ReadAllText($_.FullName)
  $content = $content.Replace('${{values.name}}',$Name).Replace('${{values.owner}}',$Owner).Replace('${{values.environment}}',$Environment).Replace('${{values.database}}',$Database).Replace('${{values.repoOwner}}',$RepoOwner.ToLowerInvariant()).Replace('${{values.domain}}',$Domain).Replace('${{values.observability}}','true')
  [IO.File]::WriteAllText($_.FullName,$content,$encoding)
}
Write-Host "Generated $outputPath"
