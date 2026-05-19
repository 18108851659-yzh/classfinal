@echo off
call "D:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvarsall.bat" x86_amd64
set JAVA_HOME=D:\env\jdk\openjdk-21_windows-x64_bin\jdk-21
set INCLUDE=%JAVA_HOME%\include;%JAVA_HOME%\include\win32;e:\baohu\classfinal\classfinal-core\src\main\jni;%INCLUDE%
cl.exe /LD /O2 /MD "e:\baohu\classfinal\classfinal-core\src\main\jni\classfinal_native.c" /Fe:"e:\baohu\classfinal\classfinal-core\src\main\resources\classfinal_native.dll" /link
