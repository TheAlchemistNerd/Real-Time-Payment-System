param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,23}$')]
    [string]$Run
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$project = "rtps-test-$Run-$([Guid]::NewGuid().ToString('N').Substring(0,10))"
$evidence = Join-Path $repository "logs/validation/$project"
$null = New-Item -ItemType Directory -Path $evidence
$log = Join-Path $evidence 'commands.log'
$envFile = Join-Path $evidence 'stack.env'
$password = [Guid]::NewGuid().ToString('N')
$settings = [ordered]@{
    DB_NAME='payment_db'; DB_USERNAME='payment_user'; DB_PASSWORD=$password
    TRANSACTION_PORT='0'; PAYMENT_PORT='0'; FRAUD_PORT='0'; NOTIFICATION_PORT='0'
    KAFKA_PORT='0'; KAFKA_ADVERTISED_PORT='9092'; MAILPIT_PORT='0'
}
$previousEnvironment = @{}
$settings.GetEnumerator() | ForEach-Object {
    $previousEnvironment[$_.Key] = [Environment]::GetEnvironmentVariable($_.Key, 'Process')
    [Environment]::SetEnvironmentVariable($_.Key, $null, 'Process')
    "$($_.Key)=$($_.Value)"
} | Set-Content -LiteralPath $envFile -Encoding Ascii
$compose = @('compose','--project-name',$project,'--env-file',$envFile,'--file',(Join-Path $repository 'docker-compose.yml'))
$cleanupCompose = $compose
$services = @('transaction-service','payment-service','fraud-detection-service','notification-service')
$started = [DateTimeOffset]::UtcNow.ToString('o')
$exitCode = 1
$ownedProject = $false
$observations = [ordered]@{}
$bases = @{}

function Invoke-Docker {
    param([string[]]$Arguments)
    $display = ("docker " + ($Arguments -join ' ')).Replace($password,'[local-password]')
    Write-Host $display -ForegroundColor Cyan
    Add-Content -LiteralPath $log -Value $display
    $oldAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $lines = [Collections.Generic.List[string]]::new()
    try {
        & docker @Arguments 2>&1 | ForEach-Object {
            $line = "$PSItem".Replace($password,'[local-password]')
            $lines.Add($line)
            Write-Host $line
            Add-Content -LiteralPath $log -Value $line
        }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldAction
    }
    Add-Content -LiteralPath $log -Value "Exit code $code"
    if ($code -ne 0) { throw "Docker exited $code. See $log." }
    return $lines.ToArray()
}
function Invoke-Compose {
    param([string[]]$Arguments)
    Invoke-Docker ($compose + $Arguments)
}
function Read-Sql {
    param([string]$Sql)
    $lines = @(Invoke-Compose @('exec','-T','postgres','psql','-U','payment_user','-d','payment_db','-v','ON_ERROR_STOP=1','-At','-c',$Sql))
    return [string]::Join([Environment]::NewLine, $lines).Trim()
}
function Assert-Equal {
    param($Actual, $Expected, [string]$Purpose)
    if ($Actual -ne $Expected) { throw "$Purpose. Expected $Expected, received $Actual." }
}
function Read-Health {
    for ($index=0; $index -lt $services.Count; $index++) {
        $service = $services[$index]
        $mapping = @(Invoke-Compose @('port',$service,"$(8080+$index)"))[-1]
        $port = ($mapping -split ':')[-1]
        $bases[$service] = "http://127.0.0.1:$port"
        $response = Invoke-RestMethod -Uri ($bases[$service] + '/actuator/health') -TimeoutSec 15
        Assert-Equal $response.status 'UP' "$service health"
    }
}
function Assert-KafkaSubscriptions {
    $expected = @(
        @('payment-service','create-transaction'),
        @('payment-saga','fraud-check-completed'), @('payment-saga','payment-processed'), @('payment-saga','payment-failed'),
        @('transaction-projection','transaction-events'), @('fraud-detection-service','fraud-check-requested'),
        @('notification-service','transaction-completed'), @('notification-service-fraud','fraud-check-completed'),
        @('notification-service-payment','payment-failed')
    )
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    do {
        $lines = @(Invoke-Compose @('exec','-T','-e','KAFKA_HEAP_OPTS=-Xms16m -Xmx128m','kafka','kafka-consumer-groups','--bootstrap-server','kafka:29092','--all-groups','--describe'))
        $missing = @($expected | Where-Object {
            $pattern = '^' + [regex]::Escape($_[0]) + '\s+' + [regex]::Escape($_[1]) +
                '\s+\d+\s+\S+\s+\S+\s+\S+\s+(?!-\s)\S+\s+(?!-\s)\S+\s+\S+'
            !($lines -match $pattern)
        })
        if ($missing.Count -eq 0) { return }
        Start-Sleep -Seconds 3
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Expected Kafka consumer subscriptions were not observed.'
}
function Assert-PreservedData {
    Assert-Equal (Read-Sql "SELECT count(*) FROM domain_events WHERE aggregate_identifier='$transactionId' AND payload->>'transactionId'='$transactionId' AND payload->>'currency'='USD' AND payload->>'userId'='b2-local-user' AND (payload->>'amount')::numeric=12.34 AND payload->>'paymentMethod'='CREDIT_CARD'") '1' 'Persisted intake event'
    Assert-Equal (Read-Sql "SELECT amount::text || ':' || currency || ':' || status FROM transaction_read_model WHERE transaction_id='b2-query-fixture'") '12.34:USD:COMPLETED' 'Read-model fixture'
    Assert-Equal (Read-Sql "SELECT id || ':' || md5sum FROM databasechangelog ORDER BY orderexecuted") $checksum 'Migration history'
    $summary = Invoke-RestMethod -Uri ($bases['payment-service']+'/api/v1/payments/summary') -TimeoutSec 15
    Assert-Equal $summary.totalTransactions 1 'Database-backed summary'
    Assert-Equal $summary.completedTransactions 1 'Completed fixture count'
}

Push-Location -LiteralPath $repository
try {
    $configured = @(Invoke-Compose @('config','--services'))
    $required = $services + @('postgres','kafka','mailpit','fraud-stub')
    $missing = @($required | Where-Object { $configured -notcontains $_ })
    if ($missing.Count) { throw "Compose is missing required services: $($missing -join ', ')." }
    foreach ($service in $services) {
        $jar = Join-Path $repository "$service/target/$service-1.0.0.jar"
        if (!(Test-Path -LiteralPath $jar)) { throw "Build the backend first. Missing $jar." }
    }
    foreach ($resource in 'container','volume','network') {
        $listArguments = @($resource,'ls','--filter',"label=com.docker.compose.project=$project",'-q')
        if ($resource -eq 'container') { $listArguments += '--all' }
        $existing = @(Invoke-Docker $listArguments)
        if ($existing.Count -gt 0) { throw "Test project already has $resource resources." }
    }
    $ownedProject = $true
    Invoke-Compose @('up','--build','--detach','--wait','--wait-timeout','600') | Out-Null
    Read-Health
    Assert-KafkaSubscriptions
    $observations.freshStartup = 'All four applications UP; expected Kafka assignments present'
    $observations.sourcePostgresVersion = Read-Sql "SHOW server_version"
    Assert-Equal (Read-Sql 'SELECT count(*) FROM domain_events') '0' 'Fresh event store'
    Assert-Equal (Read-Sql 'SELECT count(*) FROM transaction_read_model') '0' 'Fresh read model'

    $request = @{userId='b2-local-user';amount=12.34;currency='USD';paymentMethod='CREDIT_CARD';description='B2 intake persistence'}
    $response = Invoke-WebRequest -UseBasicParsing -Uri ($bases['transaction-service']+'/api/v1/transactions') -Method Post -ContentType 'application/json' -Body ($request|ConvertTo-Json) -TimeoutSec 20
    Assert-Equal $response.StatusCode 202 'Transaction intake response'
    $body = $response.Content | ConvertFrom-Json
    Assert-Equal $body.status 'PENDING' 'Intake status'
    $transactionId = $body.transactionId
    if ($transactionId -notmatch '^TX-[a-zA-Z0-9-]+$') { throw 'Unexpected transaction ID.' }
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        $count = Read-Sql "SELECT count(*) FROM domain_events WHERE aggregate_identifier='$transactionId'"
        if ($count -eq '1') { break }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    Assert-Equal $count '1' 'HTTP-to-Kafka-to-PostgreSQL intake'
    $checksum = Read-Sql "SELECT id || ':' || md5sum FROM databasechangelog ORDER BY orderexecuted"
    if (!$checksum.StartsWith('001-payment-schema:')) { throw 'Expected applied baseline migration.' }
    Read-Sql "INSERT INTO transaction_read_model(transaction_id,user_id,amount,currency,payment_method,status,created_at) VALUES('b2-query-fixture','b2-local-user',12.34,'USD','CREDIT_CARD','COMPLETED',CURRENT_TIMESTAMP)" | Out-Null
    Assert-PreservedData
    $observations.intake = $transactionId
    $observations.queryFixture = 'Explicit SQL fixture verified independently through the payment summary'

    Invoke-Compose (@('restart') + $services) | Out-Null
    Invoke-Compose @('up','--detach','--no-build','--wait','--wait-timeout','600') | Out-Null
    Read-Health
    Assert-PreservedData
    $observations.restart = 'Event, read model and migration checksum retained'
    Invoke-Compose @('down','--timeout','30') | Out-Null
    Invoke-Compose @('up','--detach','--no-build','--wait','--wait-timeout','600') | Out-Null
    Read-Health
    Assert-KafkaSubscriptions
    Assert-PreservedData
    $observations.recreation = 'Containers recreated using retained volumes; data and migration checksum retained'
    Invoke-Compose @('images','--format','json') | Set-Content -LiteralPath (Join-Path $evidence 'images.json')
    $exitCode = 0
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Add-Content -LiteralPath $log -Value $_.Exception.Message
} finally {
    if ($ownedProject) {
        try {
            $containers = @(Invoke-Compose @('ps','--all','--quiet'))
            if ($containers.Count -gt 0) {
                Invoke-Docker (@('inspect','--format','{{.Name}} {{json .State}}') + $containers) |
                    Set-Content -LiteralPath (Join-Path $evidence 'container-state.log')
            }
        } catch {
            $exitCode = 1
            Write-Host "State capture failed for $project. $($_.Exception.Message)" -ForegroundColor Red
        }
        try {
            Invoke-Compose @('logs','--no-color','--tail','250') | Set-Content -LiteralPath (Join-Path $evidence 'services.log')
        } catch {
            $exitCode = 1
            Write-Host "Log capture failed for $project. $($_.Exception.Message)" -ForegroundColor Red
        }
        try {
            Invoke-Docker ($cleanupCompose + @('down','--volumes','--remove-orphans','--timeout','30')) | Out-Null
        } catch {
            $exitCode = 1
            Write-Host "Cleanup needs attention for $project. $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    foreach ($key in $settings.Keys) { [Environment]::SetEnvironmentVariable($key,$previousEnvironment[$key],'Process') }
    [ordered]@{run=$Run;project=$project;exitCode=$exitCode;started=$started;finished=[DateTimeOffset]::UtcNow.ToString('o');observations=$observations} |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'result.json')
    Pop-Location
}
Write-Host "Stack verification exit code $exitCode. Evidence $evidence" -ForegroundColor Yellow
exit $exitCode
