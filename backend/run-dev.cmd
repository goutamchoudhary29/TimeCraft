@echo off
SETLOCAL
IF EXIST "C:\Program Files\Java\jdk-21\bin\java.exe" (
  SET "JAVA_HOME=C:\Program Files\Java\jdk-21"
  SET "PATH=C:\Program Files\Java\jdk-21\bin;%PATH%"
)
IF "%DB_USERNAME%"=="" SET "DB_USERNAME=root"
IF "%DB_PASSWORD%"=="" SET "DB_PASSWORD=992643"
IF "%ADMIN_EMAIL%"=="" SET "ADMIN_EMAIL=admin@timecraft.example"
IF "%ADMIN_PASSWORD%"=="" SET "ADMIN_PASSWORD=AdminPassword123"
IF "%RAZORPAY_KEY_ID%"=="" SET "RAZORPAY_KEY_ID=rzp_test_U3htQuCaATwCT0"
IF "%RAZORPAY_KEY_SECRET%"=="" SET "RAZORPAY_KEY_SECRET=UFDRy89u2WDK8n6pobvj8Hz5"
SET "RAZORPAY_TEST_SANDBOX=false"
REM --- Gmail SMTP Configuration for Real OTP & Order Emails ---
SET "MAIL_ENABLED=true"
SET "MAIL_HOST=smtp.gmail.com"
SET "MAIL_PORT=587"
SET "MAIL_USERNAME=supporttimecraft@gmail.com"
SET "MAIL_PASSWORD=ocfzvcvyyasksviv"
SET "MAIL_FROM=TimeCraft <supporttimecraft@gmail.com>"
cd /d "%~dp0"
echo Starting TimeCraft on http://localhost:8080 (Admin: %ADMIN_EMAIL% / %ADMIN_PASSWORD%)
call .\mvnw.cmd spring-boot:run %*

