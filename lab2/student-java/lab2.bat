@echo off
if exist "%~dp0build\classes\java\main" (
    java -cp "%~dp0build\classes\java\main" lab2.Main %*
) else (
    java -cp "%~dp0out" lab2.Main %*
)
