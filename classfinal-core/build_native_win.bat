@echo off
setlocal enabledelayedexpansion

:: 尝试自动查找 Visual Studio 环境
if not defined INCLUDE (
    echo Detecting Visual Studio environment...
    
    :: 尝试 VS2022
    if exist "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat" (
        call "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
    ) else if exist "C:\Program Files\Microsoft Visual Studio\2022\Professional\VC\Auxiliary\Build\vcvars64.bat" (
        call "C:\Program Files\Microsoft Visual Studio\2022\Professional\VC\Auxiliary\Build\vcvars64.bat"
    ) else if exist "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat" (
        call "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat"
    ) else if exist "D:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat" (
        call "D:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
    ) else if exist "D:\Program Files\Microsoft Visual Studio\2022\Professional\VC\Auxiliary\Build\vcvars64.bat" (
        call "D:\Program Files\Microsoft Visual Studio\2022\Professional\VC\Auxiliary\Build\vcvars64.bat"
    ) else if exist "D:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat" (
        call "D:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat"
    )
    
    :: 尝试 VS2019
    else if exist "C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvars64.bat" (
        call "C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvars64.bat"
    ) else if exist "C:\Program Files (x86)\Microsoft Visual Studio\2019\Professional\VC\Auxiliary\Build\vcvars64.bat" (
        call "C:\Program Files (x86)\Microsoft Visual Studio\2019\Professional\VC\Auxiliary\Build\vcvars64.bat"
    ) else if exist "C:\Program Files (x86)\Microsoft Visual Studio\2019\Enterprise\VC\Auxiliary\Build\vcvars64.bat" (
        call "C:\Program Files (x86)\Microsoft Visual Studio\2019\Enterprise\VC\Auxiliary\Build\vcvars64.bat"
    )
    
    :: 尝试 Build Tools
    else if exist "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat" (
        call "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
    ) else if exist "C:\Program Files\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" (
        call "C:\Program Files\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
    )
    
    else (
        echo ERROR: Could not find Visual Studio vcvars64.bat
        echo Please run this script from "Developer Command Prompt for Visual Studio"
        echo Or install Visual Studio with C++ workload
        exit /b 1
    )
)

set JAVA_HOME=%JAVA_HOME%
if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME is not set
    exit /b 1
)

set JNI_DIR=%~dp0src\main\jni
set BUILD_DIR=%~dp0build\native
set OUTPUT_DIR=%~dp0src\main\resources

if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

:: 检测 Windows SDK 路径
if not defined WindowsSdkDir (
    if exist "C:\Program Files (x86)\Windows Kits\10" (
        set WindowsSdkDir=C:\Program Files (x86)\Windows Kits\10
    ) else if exist "D:\Windows Kits\10" (
        set WindowsSdkDir=D:\Windows Kits\10
    ) else (
        echo ERROR: Could not find Windows SDK
        exit /b 1
    )
)

:: 检测 SDK 版本
for /f "delims=" %%i in ('dir /b /ad "%WindowsSdkDir%\Include" 2^>nul ^| findstr "^10\." ^| sort /r') do (
    set SDK_VERSION=%%i
    goto :found_sdk
)
:found_sdk

if not defined SDK_VERSION (
    echo ERROR: Could not find Windows SDK version
    exit /b 1
)

echo Windows SDK: %WindowsSdkDir%
echo SDK Version: %SDK_VERSION%
echo JAVA_HOME: %JAVA_HOME%

echo Compiling classfinal_native.dll ...

cl /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" /I"%JNI_DIR%" ^
   /I"%WindowsSdkDir%\Include\%SDK_VERSION%\ucrt" ^
   /I"%WindowsSdkDir%\Include\%SDK_VERSION%\um" ^
   /I"%WindowsSdkDir%\Include\%SDK_VERSION%\shared" ^
   /LD /O2 /MD ^
   "%JNI_DIR%\classfinal_native.c" ^
   /Fe:"%OUTPUT_DIR%\classfinal_native.dll" ^
   /link

if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to compile classfinal_native.dll
    exit /b 1
)

echo Successfully built: %OUTPUT_DIR%\classfinal_native.dll

endlocal
