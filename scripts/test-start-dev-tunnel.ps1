$ErrorActionPreference = 'Stop'
$source = Join-Path (Split-Path -Parent $PSScriptRoot) 'start-dev.ps1'
$tokens = $null
$errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw "start-dev.ps1 parse failed: $($errors -join '; ')" }

$parameters = @($ast.ParamBlock.Parameters | ForEach-Object { $_.Name.VariablePath.UserPath })
foreach ($name in @('Tunnel', 'NoTunnel')) {
    if ($parameters -notcontains $name) { throw "Missing parameter: $name" }
}

$calls = @($ast.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.CommandAst] -and
        $node.GetCommandName() -eq 'Start-PublicTunnel'
}, $true))
if ($calls.Count -ne 1) { throw "Expected exactly one tunnel call, found $($calls.Count)" }
$call = $calls[0]
$parent = $call.Parent
while ($parent -and $parent -isnot [System.Management.Automation.Language.IfStatementAst]) {
    $parent = $parent.Parent
}
if (-not $parent -or $parent.Clauses.Count -ne 1) { throw 'Tunnel call must belong to one if clause' }
$condition = $parent.Clauses[0].Item1
$body = $parent.Clauses[0].Item2
if ($call.Extent.StartOffset -lt $body.Extent.StartOffset -or
    $call.Extent.EndOffset -gt $body.Extent.EndOffset) { throw 'Tunnel call is not in the if body' }

# Only evaluate a side-effect-free condition and the call to the local stub.
if (@($condition.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] }, $true)).Count -ne 0) {
    throw 'Unexpected command in tunnel condition'
}
if ($call.Extent.Text -notmatch '^Start-PublicTunnel\s+-Port\s+\$frontPort$') {
    throw 'Unexpected tunnel call; refusing to execute extracted expression'
}
$testBody = $ast.ParamBlock.Extent.Text + "`n" +
    'if (' + $condition.Extent.Text + ') { ' + $call.Extent.Text + ' }'
$invoke = [scriptblock]::Create($testBody)
function Start-PublicTunnel { param([int]$Port) $script:callCount++ }
$frontPort = 3000
$flagBlocks = @($ast.FindAll({
    param($n)
    $n -is [System.Management.Automation.Language.IfStatementAst] -and
        $n.Extent.Text -match 'CLOUDWIKI_DISABLE_LOCAL_ENV'
}, $true))
if ($flagBlocks.Count -ne 1) { throw 'Expected one signal assignment block' }
$flagBlock = $flagBlocks[0]
if ($flagBlock.Extent.Text -notmatch "(?s)^if \(\`$Tunnel -and -not \`$NoTunnel\) \{\s*\`$env:CLOUDWIKI_DISABLE_LOCAL_ENV = '1'\s*\} else \{\s*\`$env:CLOUDWIKI_DISABLE_LOCAL_ENV = '0'\s*\}$") {
    throw 'Unexpected signal assignment; refusing to execute'
}
$frontStarts = @($ast.FindAll({
    param($n)
    $n -is [System.Management.Automation.Language.CommandAst] -and
        $n.GetCommandName() -eq 'Start-Process' -and
        ($n.Extent.Text -match 'npm run dev|npx vite preview')
}, $true))
if ($frontStarts.Count -ne 2) { throw 'Expected dev and preview frontend launches' }
foreach ($start in $frontStarts) {
    if ($flagBlock.Extent.EndOffset -ge $start.Extent.StartOffset) { throw 'Signal set after frontend launch' }
}
function Start-Process {
    param($FilePath, $ArgumentList, $WorkingDirectory, $RedirectStandardOutput, $RedirectStandardError, $WindowStyle, [switch]$PassThru)
    $script:capturedFlag = $env:CLOUDWIKI_DISABLE_LOCAL_ENV
}
$frontDir = 'fixture'; $frontLog = 'fixture'; $frontErr = 'fixture'
$cases = @(
    @{ Name = 'default'; Args = @{}; Expected = 0; Flag = '0' },
    @{ Name = 'Tunnel'; Args = @{ Tunnel = $true }; Expected = 1; Flag = '1' },
    @{ Name = 'NoTunnel'; Args = @{ NoTunnel = $true }; Expected = 0; Flag = '0' },
    @{ Name = 'both'; Args = @{ Tunnel = $true; NoTunnel = $true }; Expected = 0; Flag = '0' }
)
foreach ($case in $cases) {
    $script:callCount = 0
    $arguments = $case.Args
    & $invoke @arguments
    if ($script:callCount -ne $case.Expected) {
        throw "$($case.Name): expected $($case.Expected) calls, got $script:callCount"
    }
    foreach ($start in $frontStarts) {
        $env:CLOUDWIKI_DISABLE_LOCAL_ENV = 'stale'
        $script:capturedFlag = $null
        $frontTest = [scriptblock]::Create($ast.ParamBlock.Extent.Text + "`n" + $flagBlock.Extent.Text + "`n" + $start.Extent.Text)
        & $frontTest @arguments
        if ($script:capturedFlag -ne $case.Flag) { throw "$($case.Name): frontend signal mismatch" }
    }
    Write-Host "$($case.Name): $script:callCount calls"
}
Write-Host 'PowerShell AST parse and tunnel switch checks passed.'
