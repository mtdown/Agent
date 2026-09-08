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

function Get-GitText {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & git @Arguments
    if ($null -eq $output) {
        return ''
    }
    return (($output | Out-String).Trim())
}

function Assert-GitAvailable {
    if ($DryRun) {
        Write-Host '[dry-run] git --version'
        return
    }

    $version = & git --version 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($version)) {
        throw 'git is not available in this session. Run upload.ps1 from a terminal where git works, or upload manually.'
    }
    Write-Host "git: $version"
}

Assert-GitAvailable

if ($DryRun) {
    Write-Host '[dry-run] git branch --show-current'
    $branchName = ''
} else {
    $branchName = Get-GitText @('branch', '--show-current')
}
if ([string]::IsNullOrWhiteSpace($branchName)) {
    throw 'Unable to determine the current branch. This usually means git produced no output (blocked or not on PATH).'
}

Write-Host "Current branch: $branchName"
if ($branchName -in @('main', 'master')) {
    throw 'Refusing to upload from main/master. Create a task branch with start-task.ps1 first.'
}

$upstream = ''
try {
    $upstream = (Get-GitText @('rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{u}')).Trim()
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

# Verify the push really landed: the upstream must contain HEAD.
if (-not $DryRun) {
    Run-Git @('fetch', 'origin')
    $ahead = Get-GitText @('rev-list', '--count', "$upstream..HEAD")
    if ($ahead -ne '0') {
        throw "Push did not take effect: $ahead commit(s) still missing on $upstream."
    }
    Write-Host "Verified: $upstream is up to date with HEAD."
}

Write-Host ''
Write-Host "Upload complete: $upstream"
Write-Host 'Create a merge request / pull request from this branch into main after review.'
