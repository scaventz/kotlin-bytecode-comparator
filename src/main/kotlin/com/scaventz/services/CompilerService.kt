package com.scaventz.services

import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit


object CompilerService {
    private val log = Logger.getInstance(this::class.java)

    fun verify(file: File): String? {
        val bin = file.listFiles()?.singleOrNull {
            it.name == "bin" && it.isDirectory
        } ?: return null

        val kotlinc = bin.listFiles()?.singleOrNull {
            it.name == "kotlinc.bat" && !it.isDirectory
        } ?: return null

        return Processes.run("cmd", "/c", "kotlinc.bat", "-version", workingDir = bin)
    }
}

internal object Processes {
    private val NEWLINE = System.getProperty("line.separator")

    /**
     * @param command the command to run
     * @return the output of the command
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    fun run(vararg command: String, workingDir: File): String {
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
