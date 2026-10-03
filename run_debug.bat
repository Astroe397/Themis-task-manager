@echo off
cd /d "C:\Users\adr\Desktop\New folder (9)"
echo Starting Eel App... > run_log.txt
python main.py >> run_log.txt 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Python exited with error code %ERRORLEVEL% >> run_log.txt
)
pause