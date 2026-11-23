@echo off
SETLOCAL
IF EXIST "C:\Program Files\Java\jdk-21\bin\java.exe" (
  SET "JAVA_HOME=C:\Program Files\Java\jdk-21"
  SET "PATH=C:\Program Files\Java\jdk-21\bin;%PATH%"
)
cd /d "%~dp0"
call .\mvnw.cmd test %*
