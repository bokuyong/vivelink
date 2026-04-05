@echo off
setlocal
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set GRADLE_LAUNCHER=C:\Users\bokuy\.gradle\wrapper\dists\gradle-8.12-all\ejduaidbjup3bmmkhw3rie4zb\gradle-8.12\lib\gradle-launcher-8.12.jar
set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set APP_HOME=%~dp0

cd /d "%APP_HOME%"
"%JAVA_HOME%\bin\java.exe" -Dorg.gradle.appname=gradlew -classpath "%GRADLE_LAUNCHER%" org.gradle.launcher.GradleMain %*
