param(
    [ValidateSet("all", "backend", "web", "admin")]
    [string]$Module = "all",
    [string]$DistDir = "release-dist"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

if ($Module -eq "all" -or $Module -eq "backend") {
    Write-Host "==> 构建后端模块 (cloud-api, cloud-worker)..."
    & mvn -f backend/pom.xml -pl cloud-api,cloud-worker -am -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Copy-Item -Force backend/cloud-api/target/*.jar (Join-Path $DistDir "cloud-api.jar") -ErrorAction SilentlyContinue
    Copy-Item -Force backend/cloud-worker/target/*.jar (Join-Path $DistDir "cloud-worker.jar") -ErrorAction SilentlyContinue
}

if ($Module -eq "all" -or $Module -eq "web") {
    Write-Host "==> 构建用户端前端 (@cloud/web)..."
    Push-Location frontend/apps/web
    try {
        & .\node_modules\.bin\vue-tsc -p tsconfig.app.json --noEmit --incremental false
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        $webOut = (Resolve-Path (Join-Path "..\..\..\" $DistDir)).Path + "\web"
        & .\node_modules\.bin\vite build --configLoader native --outDir $webOut
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
}

if ($Module -eq "all" -or $Module -eq "admin") {
    Write-Host "==> 构建管理后台前端 (@cloud/admin)..."
    Push-Location frontend/apps/admin
    try {
        & .\node_modules\.bin\vue-tsc -p tsconfig.app.json --noEmit --incremental false
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        $adminOut = (Resolve-Path (Join-Path "..\..\..\" $DistDir)).Path + "\admin"
        & .\node_modules\.bin\vite build --configLoader native --outDir $adminOut
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
}

Write-Host "发布产物构建完成：$DistDir"
