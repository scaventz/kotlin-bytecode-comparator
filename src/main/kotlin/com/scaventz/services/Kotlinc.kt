package com.scaventz.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.psi.PsiFile
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class Kotlinc {
    // private val log = Logger.getInstance(this::class.java)
    private val propertyGraph = PropertyGraph()
    var bin: File? = null
    val inline = propertyGraph.lazyProperty { true }
    val optimization = propertyGraph.lazyProperty { true }
    val assertions = propertyGraph.lazyProperty { true }
    val jvmIR = propertyGraph.lazyProperty { true }
    val fir = propertyGraph.lazyProperty { false }

    val version by lazy {
        val info = Processes.run("cmd", "/c", "kotlinc.bat", "-version", workingDir = bin!!)
        info.substringAfter("info: ").trim()
    }

    fun compile(file: PsiFile, destination: File) {
        val src = file.virtualFile.canonicalPath ?: return
        val command = mutableListOf("cmd", "/c", "kotlinc.bat")
        command.add(src)
        when {
            !jvmIR.get() -> command.add("-Xuse-old-backend")
            !inline.get() -> command.add("-Xno-inline")
            fir.get() -> command.add("-Xuse-fir")
        }
        command.add("-d")
        command.add(destination.path)
        Processes.run(*command.toTypedArray(), workingDir = bin!!)
    }

    fun decompile(dir: File): Map<String, String> {
        val classes = dir.listFiles()?.filter {
            it.name.endsWith(".class")
        } ?: return mapOf()
        val result = mutableMapOf<String, String>()
        classes.forEach {
            val decompiled =
                Processes.run("cmd", "/c", "javap", "-c", "-p", "-v", "-l", it.path, workingDir = File("/"))
            result[it.canonicalPath] = decompiled
        }
        return result
    }
}

internal object Processes {
    private val log = Logger.getInstance(this::class.java)
    private val NEWLINE = System.getProperty("line.separator")

    /**
     * @param command the command to run
     * @return the output of the command
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    fun run(vararg command: String, workingDir: File): String {
        log.info("command: ${command.toList()}")
        val pb = ProcessBuilder(*command)
            .directory(workingDir)
            .redirectErrorStream(true)
        val process = pb.start()
        val result = StringBuilder(80)
        BufferedReader(InputStreamReader(process.inputStream)).use { `in` ->
            while (true) {
                val line = `in`.readLine() ?: break
                result.append(line).append(NEWLINE)
            }
        }
        return result.toString()
    }
}
