[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [switch]$InstallMySql,
    [switch]$SkipDatabaseInit,
    [switch]$OverwriteEnvFile,
    [string]$MySqlHost = "localhost",
    [int]$MySqlPort = 3306,
    [string]$MySqlDatabase = "chat",
    [string]$MySqlUsername = "root",
    [string]$MySqlPassword = "",
    [string]$ServerPort = "8080",
    [string]$OpenRouterApiKey = "",
    [string]$OpenRouterModel = "x-ai/grok-4.1-fast",
    [string]$ImageApiKey = "",
    [string]$ImageApiBaseUrl = "https://right.codes/gpt/v1",
    [string]$ImageApiModel = "gpt-image-2"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-InfoLine {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Gray
}

function Write-WarnLine {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-SuccessLine {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Test-CommandAvailable {
    param([string]$Name)
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Assert-FileExists {
    param(
        [string]$Path,
        [string]$Label
    )
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Label not found: $Path"
    }
}

function Get-PlainTextFromSecureString {
    param([System.Security.SecureString]$SecureString)
    if ($null -eq $SecureString) {
        return ""
    }

    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Escape-ForDoubleQuotedString {
    param([string]$Value)
    if ($null -eq $Value) {
        return ""
    }

    return $Value.Replace("`", "``").Replace('"', '`"')
}

function Invoke-WingetInstall {
    param(
        [string]$PackageId,
        [string]$DisplayName,
        [switch]$Interactive
    )

    if (-not (Test-CommandAvailable "winget")) {
        throw "winget was not found. Please install App Installer from Microsoft Store first."
    }

    Write-Step "Installing $DisplayName with winget"
    $arguments = @(
        "install",
        "--id", $PackageId,
        "--exact",
        "--accept-package-agreements",
        "--accept-source-agreements"
    )

    if (-not $Interactive) {
        $arguments += "--silent"
    }

    & winget @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "winget install failed for $DisplayName (package id: $PackageId)."
    }
}

function Get-JavaMajorVersion {
    param([string]$JavaExecutable = "java")

    try {
        $versionOutput = & $JavaExecutable -version 2>&1 | Out-String
    } catch {
        return 0
    }

    if ($versionOutput -match 'version "(\d+)(?:\.\d+)?') {
        return [int]$Matches[1]
    }

    return 0
}

function Find-JavaHome {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        $javaBin = Split-Path -Parent $javaCommand.Source
        return Split-Path -Parent $javaBin
    }

    $searchRoots = @(
        "C:\Program Files\Microsoft",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java"
    )

    foreach ($root in $searchRoots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }

        $candidates = Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending

        foreach ($candidate in $candidates) {
            $javaExe = Join-Path $candidate.FullName "bin\java.exe"
            if (-not (Test-Path -LiteralPath $javaExe)) {
                continue
            }

            $majorVersion = Get-JavaMajorVersion -JavaExecutable $javaExe
            if ($majorVersion -ge 21) {
                return $candidate.FullName
            }
        }
    }

    return ""
}

function Ensure-PathContains {
    param([string]$Directory)

    if ([string]::IsNullOrWhiteSpace($Directory)) {
        return
    }

    $segments = @($env:Path -split ";" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($segments -notcontains $Directory) {
        $env:Path = "$Directory;$env:Path"
    }
}

function Ensure-Java21 {
    $currentMajor = Get-JavaMajorVersion
    if ($currentMajor -ge 21) {
        Write-SuccessLine "Java $currentMajor detected."
    } else {
        Invoke-WingetInstall -PackageId "Microsoft.OpenJDK.21" -DisplayName "Microsoft OpenJDK 21"
    }

    $javaHome = Find-JavaHome
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        throw "Java 21 was not found after installation. Please reopen PowerShell and run the script again."
    }

    $env:JAVA_HOME = $javaHome
    Ensure-PathContains -Directory (Join-Path $javaHome "bin")
    Write-SuccessLine "JAVA_HOME set to $javaHome for the current session."
}

function Find-MySqlExecutable {
    $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
    if ($null -ne $mysqlCommand) {
        return $mysqlCommand.Source
    }

    $searchRoots = @(
        "C:\Program Files\MySQL",
        "C:\Program Files (x86)\MySQL"
    )

    foreach ($root in $searchRoots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }

        $candidate = Get-ChildItem -Path $root -Recurse -Filter "mysql.exe" -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match "bin\\mysql\.exe$" } |
            Select-Object -First 1

        if ($null -ne $candidate) {
            return $candidate.FullName
        }
    }

    return ""
}

function Ensure-MySqlAvailability {
    $mysqlExe = Find-MySqlExecutable
    if (-not [string]::IsNullOrWhiteSpace($mysqlExe)) {
        Ensure-PathContains -Directory (Split-Path -Parent $mysqlExe)
        Write-SuccessLine "MySQL client detected at $mysqlExe."
        return $mysqlExe
    }

    if (-not $InstallMySql) {
        Write-WarnLine "mysql.exe was not found. Install MySQL Server 8.x manually or rerun this script with -InstallMySql."
        return ""
    }

    try {
        Invoke-WingetInstall -PackageId "Oracle.MySQL" -DisplayName "MySQL Installer Community" -Interactive
    } catch {
        Write-WarnLine $_.Exception.Message
        Write-WarnLine "MySQL was not installed automatically. Please install MySQL Server 8.x manually and rerun the script."
        return ""
    }

    Write-WarnLine "If the MySQL installer opened a GUI wizard, finish the server setup first and then rerun this script."

    $mysqlExe = Find-MySqlExecutable
    if (-not [string]::IsNullOrWhiteSpace($mysqlExe)) {
        Ensure-PathContains -Directory (Split-Path -Parent $mysqlExe)
        Write-SuccessLine "MySQL client detected at $mysqlExe."
        return $mysqlExe
    }

    return ""
}

function Assert-DatabaseNameSafe {
    param([string]$DatabaseName)

    if ($DatabaseName -notmatch '^[A-Za-z0-9_]+$') {
        throw "MySqlDatabase only supports letters, numbers and underscores in this script."
    }
}

function Initialize-Database {
    param(
        [string]$MySqlExecutable,
        [string]$SchemaPath
    )

    if ($SkipDatabaseInit) {
        Write-InfoLine "Skipping database initialization because -SkipDatabaseInit was provided."
        return
    }

    if ([string]::IsNullOrWhiteSpace($MySqlExecutable)) {
        Write-WarnLine "Database initialization skipped because mysql.exe is not available."
        return
    }

    Assert-DatabaseNameSafe -DatabaseName $MySqlDatabase
    Assert-FileExists -Path $SchemaPath -Label "Schema file"

    $passwordToUse = $MySqlPassword
    if ([string]::IsNullOrWhiteSpace($passwordToUse)) {
        $securePassword = Read-Host "Enter MySQL password for user '$MySqlUsername' (leave blank if none)" -AsSecureString
        $passwordToUse = Get-PlainTextFromSecureString -SecureString $securePassword
    }
    $script:MySqlPassword = $passwordToUse

    $baseArguments = @(
        "--protocol=tcp",
        "--host=$MySqlHost",
        "--port=$MySqlPort",
        "--user=$MySqlUsername",
        "--default-character-set=utf8mb4"
    )

    if (-not [string]::IsNullOrWhiteSpace($passwordToUse)) {
        $env:MYSQL_PWD = $passwordToUse
    }

    try {
        $createDatabaseSql = "CREATE DATABASE IF NOT EXISTS $MySqlDatabase DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

        Write-Step "Creating database $MySqlDatabase if needed"
        & $MySqlExecutable @baseArguments -e $createDatabaseSql
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create database $MySqlDatabase."
        }

        Write-Step "Importing schema-mysql.sql"
        $schemaSql = Get-Content -LiteralPath $SchemaPath -Raw
        $schemaSql | & $MySqlExecutable @baseArguments $MySqlDatabase
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to import schema into database $MySqlDatabase."
        }

        Write-SuccessLine "Database initialization completed."
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Write-LocalEnvFile {
    param([string]$OutputPath)

    $jdbcUrl = "jdbc:mysql://$MySqlHost`:$MySqlPort/$MySqlDatabase?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
    $content = @"
# Generated by scripts/windows/setup-chat-env.ps1
# Load it with:
#   . .\scripts\windows\chat-env.local.ps1

`$env:MYSQL_URL = "$(Escape-ForDoubleQuotedString $jdbcUrl)"
`$env:MYSQL_USERNAME = "$(Escape-ForDoubleQuotedString $MySqlUsername)"
`$env:MYSQL_PASSWORD = "$(Escape-ForDoubleQuotedString $MySqlPassword)"
`$env:OPENROUTER_API_KEY = "$(Escape-ForDoubleQuotedString $OpenRouterApiKey)"
`$env:OPENROUTER_MODEL = "$(Escape-ForDoubleQuotedString $OpenRouterModel)"
`$env:IMAGE_API_KEY = "$(Escape-ForDoubleQuotedString $ImageApiKey)"
`$env:IMAGE_API_BASE_URL = "$(Escape-ForDoubleQuotedString $ImageApiBaseUrl)"
`$env:IMAGE_API_MODEL = "$(Escape-ForDoubleQuotedString $ImageApiModel)"
`$env:SERVER_PORT = "$(Escape-ForDoubleQuotedString $ServerPort)"
"@

    $outputDir = Split-Path -Parent $OutputPath
    if (-not (Test-Path -LiteralPath $outputDir)) {
        New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    }

    if ((Test-Path -LiteralPath $OutputPath) -and -not $OverwriteEnvFile) {
        Write-WarnLine "Local env file already exists: $OutputPath"
        Write-WarnLine "Use -OverwriteEnvFile if you want this script to replace it."
        return
    }

    Set-Content -LiteralPath $OutputPath -Value $content -Encoding UTF8
    Write-SuccessLine "Local env file created: $OutputPath"
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
} else {
    $ProjectRoot = (Resolve-Path $ProjectRoot).Path
}

$schemaPath = Join-Path $ProjectRoot "src\main\resources\db\schema-mysql.sql"
$pomPath = Join-Path $ProjectRoot "pom.xml"
$mvnwPath = Join-Path $ProjectRoot "mvnw.cmd"
$envFilePath = Join-Path $ProjectRoot "scripts\windows\chat-env.local.ps1"

Assert-FileExists -Path $pomPath -Label "pom.xml"
Assert-FileExists -Path $mvnwPath -Label "mvnw.cmd"

Write-Step "Project root: $ProjectRoot"
Ensure-Java21

$mysqlExe = Ensure-MySqlAvailability
Initialize-Database -MySqlExecutable $mysqlExe -SchemaPath $schemaPath
Write-LocalEnvFile -OutputPath $envFilePath

Write-Host ""
Write-SuccessLine "Windows runtime setup completed."
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Open PowerShell in the project root."
Write-Host "2. Load local env vars:"
Write-Host "   . .\scripts\windows\chat-env.local.ps1"
Write-Host "3. Start the app:"
Write-Host "   .\mvnw.cmd spring-boot:run"
Write-Host "4. Open:"
Write-Host "   http://localhost:$ServerPort/"
Write-Host ""

if ([string]::IsNullOrWhiteSpace($OpenRouterApiKey)) {
    Write-WarnLine "OPENROUTER_API_KEY is empty in the generated env file. Chat requests will fail until you fill it."
}

if ([string]::IsNullOrWhiteSpace($ImageApiKey)) {
    Write-WarnLine "IMAGE_API_KEY is empty in the generated env file. Image requests will fail until you fill it."
}
