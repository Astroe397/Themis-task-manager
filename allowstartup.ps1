# Auto-elevate to Administrator
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
    exit
}

# Resolve target script directory dynamically
$ProjectDir =$PSScriptRoot
if (-not $ProjectDir) {$ProjectDir = "C:\Users\adr\Desktop\New folder (9)"
}

$TaskName   = "ProgressTrackerStartup"
$PythonPath = "C:\Users\adr\AppData\Local\Programs\Python\Python314\pythonw.exe"
$ScriptPath =$ProjectDir + "\main.py"

# Build Task Objects using Hash Tables (avoids inline -Parameter variable parsing)
$ActionParams = @{
    Execute          = $PythonPath
    Argument         = "`"$ScriptPath`""
    WorkingDirectory = $ProjectDir
}
$Action = New-ScheduledTaskAction @ActionParams

$Trigger = New-ScheduledTaskTrigger -AtLogOn

$PrincipalParams = @{
    UserId    = $env:USERNAME
    LogonType = "Interactive"
}
$Principal = New-ScheduledTaskPrincipal @PrincipalParams

# Register the Task
$RegisterParams = @{
    TaskName  = $TaskName
    Action    = $Action
    Trigger   = $Trigger
    Principal = $Principal
    Force     = $true
}
Register-ScheduledTask @RegisterParams

Write-Host "Task '$TaskName' registered successfully!" -ForegroundColor Green
Start-Sleep -Seconds 3