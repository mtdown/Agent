param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ -not [string]::IsNullOrWhiteSpace($_) })]
    [string]$Message,

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

function Run-Git {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $commandText = 'git ' + ($Arguments -join ' ')
    if ($DryRun) {
        Write-Host "[dry-run] $commandText"
        return
    }

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $commandText"
    }
}

$branchName = (& git branch --show-current).Trim()
if (-not $branchName) {
    throw 'Current checkout is detached. Switch to a tracked task branch before upload.'
}

Write-Host "Current branch: $branchName"
if ($branchName -in @('main', 'master')) {
    throw 'Refusing to upload from main/master. Create a task branch with start-task.ps1 first.'
}

$upstream = ''
try {
    $upstream = (& git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>$null).Trim()
} catch {
    $upstream = ''
}

if (-not $upstream) {
    throw 'Current branch has no upstream. Start branches with start-task.ps1 or set upstream before upload.'
}

if ($upstream -notlike 'origin/*') {
    throw "Current upstream is not on origin: $upstream"
}

Write-Host "Upstream branch: $upstream"
Write-Host 'Pending files:'
Write-Host 'git status --short'
& git status --short
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to read Git status.'
}

$hasChanges = [bool](& git status --porcelain)
if ($hasChanges) {
    Run-Git @('add', '-A')
    Run-Git @('commit', '-m', $Message)
} else {
    Write-Host 'No local changes to commit; pushing current branch state.'
}

Run-Git @('push')

Write-Host ''
Write-Host "Upload complete: $upstream"
Write-Host 'Create a merge request / pull request from this branch into main after review.'
