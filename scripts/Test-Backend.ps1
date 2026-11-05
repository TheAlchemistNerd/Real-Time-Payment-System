param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArguments = @('test')
)

# Run in this terminal so Maven output and the final exit code remain visible.
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$previousMavenOpts = $env:MAVEN_OPTS
$exitCode = 1
Push-Location -LiteralPath $repository
try {
    # Surefire 3.5.2 launches forks through cmd.exe on Windows. An ampersand
    # in the system temp path is misquoted by that launcher.
    if ([IO.Path]::GetTempPath().Contains('&')) {
        $testTemp = Join-Path $env:PUBLIC 'payment-backend-test-temp'
        New-Item -ItemType Directory -Path $testTemp -Force | Out-Null
        $env:MAVEN_OPTS = "$previousMavenOpts -Djava.io.tmpdir=`"$testTemp`""
    }
    Write-Host "Running Maven: $($MavenArguments -join ' ')" -ForegroundColor Cyan
    & (Join-Path $repository 'mvnw.cmd') @MavenArguments
    $exitCode = $LASTEXITCODE
} finally {
    $env:MAVEN_OPTS = $previousMavenOpts
    Pop-Location
}
Write-Host "Maven exit code: $exitCode" -ForegroundColor Yellow
exit $exitCode
