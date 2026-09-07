param(
    [string]$MainWorktree = (Join-Path $env:USERPROFILE '.config\superpowers\worktrees\Agent\main-theme-integration')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $MainWorktree)) {
    throw "Main worktree not found: $MainWorktree"
}

Push-Location $MainWorktree
try {
    $branch = (& git branch --show-current).Trim()
    if ($branch -ne 'main') {
        throw "Main preview worktree must be on main, current branch is: $branch"
    }

    & git fetch origin
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to fetch origin.'
    }

    & git pull --ff-only origin main
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to fast-forward main from origin/main.'
    }

    powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to stop existing dev services.'
    }

    powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to start main dev services.'
    }
} finally {
    Pop-Location
}
