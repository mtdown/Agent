param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('feature', 'fix')]
    [string]$Type,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9-]*$')]
    [string]$Name,

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

$suffix = if ($Type -eq 'feature') {
    ([string][char]0x5F00) + ([string][char]0x53D1)
} else {
    ([string][char]0x4FEE) + ([string][char]0x590D)
}
$branchName = "$Type/$Name$suffix"
$remoteRef = "refs/heads/$branchName"

Write-Host "Target branch: $branchName"
Write-Host 'Base branch: origin/main'

$currentBranch = Get-GitText @('branch', '--show-current')
if ($currentBranch -in @('main', 'master')) {
    Write-Host "Current branch: $currentBranch"
    Write-Host 'The task branch will be created remotely first, then checked out locally.'
}

$status = Get-GitText @('status', '--porcelain')
if ($status) {
    if ($DryRun) {
        Write-Host '[dry-run] Working tree is not clean; real branch creation would stop here.'
    } else {
        throw 'Working tree is not clean. Commit, stash, or remove local changes before starting a new task branch.'
    }
}

Run-Git @('fetch', 'origin')

$existingLocal = Get-GitText @('branch', '--list', $branchName)
if ($existingLocal) {
    throw "Local branch already exists: $branchName"
}

$existingRemote = Get-GitText @('ls-remote', '--heads', 'origin', $branchName)
if ($existingRemote) {
    throw "Remote branch already exists: origin/$branchName"
}

Run-Git @('push', 'origin', "origin/main:$remoteRef")
Run-Git @('fetch', 'origin', $branchName)
Run-Git @('switch', '--track', '-c', $branchName, "origin/$branchName")

Write-Host "Branch ready: $branchName"
Write-Host 'Next: edit files, test, then run upload.ps1 with a commit message.'
