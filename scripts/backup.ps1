param(
    [string]$OutputDir = "backups",
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbName = "cloud",
    [string]$DbUser = "root",
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$StorageRoot = "data/objects"
)

$ErrorActionPreference = "Stop"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$target = Join-Path $OutputDir "cloudlane-$stamp"
New-Item -ItemType Directory -Force -Path $target | Out-Null

Write-Host "[1/2] 导出 MySQL 数据库 ($DbName)..."
$sqlFile = Join-Path $target "$DbName.sql"
$dumpArgs = @("-h", $DbHost, "-P", "$DbPort", "-u", $DbUser, "--single-transaction", "--routines", "--triggers", $DbName)
if ($DbPassword) { $env:MYSQL_PWD = $DbPassword }
& mysqldump @dumpArgs | Out-File -FilePath $sqlFile -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw "mysqldump 执行失败 (exit=$LASTEXITCODE)" }

Write-Host "[2/2] 备份本地对象存储目录 ($StorageRoot)..."
if (Test-Path $StorageRoot) {
    Copy-Item -Recurse -Force $StorageRoot (Join-Path $target "objects")
} else {
    Write-Warning "未找到存储目录 $StorageRoot，跳过文件对象备份。"
}

Write-Host "备份完成：$target"
