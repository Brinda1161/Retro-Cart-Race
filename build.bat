@echo off
echo Compiling Kart Racer...
if not exist out mkdir out
javac -encoding UTF-8 -d out src\Main.java src\GamePanel.java src\Track.java src\Kart.java src\OpponentAI.java src\PowerUp.java
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed!
    pause
    exit /b 1
)
echo Compilation successful!
echo Running game...
java -cp out Main
pause
