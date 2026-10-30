@echo off
if exist "C:\Users\dell\tools\apache-maven-3.9.6\bin\mvn.cmd" (
    "C:\Users\dell\tools\apache-maven-3.9.6\bin\mvn.cmd" %*
) else (
    mvn.cmd %*
)
