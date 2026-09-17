@echo off
setlocal enabledelayedexpansion

echo =================================================================
echo   Building Cab Dispatch ^& Surge Pricing Simulator (CSE2006)
echo =================================================================

if not exist bin mkdir bin

echo [1/3] Compiling source files and test suite...
javac -encoding UTF-8 -cp "lib/*;bin" -d bin src/main/java/com/cabdispatch/model/*.java src/main/java/com/cabdispatch/exception/*.java src/main/java/com/cabdispatch/service/*.java src/main/java/com/cabdispatch/dao/*.java src/main/java/com/cabdispatch/thread/*.java src/main/java/com/cabdispatch/test/*.java src/main/java/com/cabdispatch/*.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo [2/3] Running automated validation test suite...
java -cp "bin;lib/*" com.cabdispatch.test.TestRunner
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Tests failed!
    exit /b %ERRORLEVEL%
)

echo [3/3] Packaging standalone executable CabDispatch.jar...
if exist CabDispatch.jar del CabDispatch.jar
jar cfe CabDispatch.jar com.cabdispatch.Main -C bin . >nul 2>&1
if not exist CabDispatch.jar (
    if exist "C:\Program Files\Java\jdk-26.0.1\bin\jar.exe" (
        "C:\Program Files\Java\jdk-26.0.1\bin\jar.exe" cfe CabDispatch.jar com.cabdispatch.Main -C bin .
    )
)

echo.
echo =================================================================
echo   BUILD SUCCESSFUL!
echo   Run simulation with: java -jar CabDispatch.jar
echo   Or run tests with:   java -cp "bin;lib/*" com.cabdispatch.test.TestRunner
echo =================================================================

endlocal
