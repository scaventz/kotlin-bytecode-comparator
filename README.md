# kotlin bytecode comparator

![Build](https://github.com/scaventz/kotlin-bytecode-comparator/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/18712.svg)](https://plugins.jetbrains.com/plugin/18712)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/18712.svg)](https://plugins.jetbrains.com/plugin/18712)

![alt text](screenshot.png)
## What is it  
<!-- Plugin description -->
For my own purposes, a plugin for comparing bytecode differences between two versions of the compiler.

It has the following defects
- Support compiling single source file only. (It calls `kotlinc` under the hood). 
- Not robust at all
- Support Windows only, but can be easily extended to support Linux/macOS
- Support `IR`, `Inline` options only
- Slow

## How to use it
Open a source file in IDE, specify paths of your compilers, click "Compile And Compare".  
I'm happy to help if you have any questions or ran into any issues.

<!-- Plugin description end -->

## How to build
`./gradlew build`  
The resulting ZIP file is located in build/distributions

