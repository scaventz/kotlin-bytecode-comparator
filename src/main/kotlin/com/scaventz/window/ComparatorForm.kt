package com.scaventz.window

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.castSafelyTo
import com.scaventz.services.Kotlinc
import java.io.File
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

class ComparatorForm(val project: Project) {
    val panel: JPanel

    private val diffPanel = DiffManager.getInstance().createRequestPanel(null, Disposer.newDisposable(), null).apply {
        putContextHints(DiffUserDataKeysEx.FORCE_DIFF_TOOL, SimpleDiffTool.INSTANCE)
    }
    private val log = Logger.getInstance(this::class.java)

    init {
        panel = panel {
            row {
                val chooserDescriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor()
                val chooseBtn1 = textFieldWithBrowseButton(fileChooserDescriptor = chooserDescriptor)
                chooseBtn1.component.isEditable = false
                chooseBtn1.text("choose compiler 1")
                chooseBtn1.component.textField.document.addDocumentListener(
                    object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            val path = chooseBtn1.component.text
                            log.info("path: $path")
                            if (path.isEmpty()) return

                            // path
                            val bin = File(path).listFiles()?.singleOrNull {
                                it.name == "bin" && it.isDirectory
                            } ?: return

                            val kotlinc = bin.listFiles()?.singleOrNull {
                                it.name == "kotlinc.bat" && !it.isDirectory
                            } ?: return

                            // get version
                            val version = Kotlinc.version(bin)
                            log.info("version: $version")
                            if (version == null || version.isEmpty()) return

                            // compile file
                            val editor = FileEditorManager.getInstance(project).selectedTextEditor
                                .castSafelyTo<EditorEx>() ?: return
                            val psi = PsiManager.getInstance(project).findFile(editor.virtualFile) ?: return
                            val src = psi.text
                            log.info("src: $src")
                            if (src == null || src.isEmpty()) return
                            val outputDir = File("d:/temp", psi.name)
                            Kotlinc.compile(bin, psi, outputDir)

                            // decompile class file
                            val map = Kotlinc.decompile(outputDir, psi.virtualFile.path)
                            val decompiled = map.map { it.value }.reduce { acc, s -> acc + "\n\n" + s }

                            // update panel
                            val request = buildRequest(
                                title1 = version.substringAfter("info: ").trim(),
                                title2 = "compiler2",
                                decompiled,
                                "124"
                            )
                            diffPanel.setRequest(request)
                        }
                    }
                )

                checkBox("Inline")
                checkBox("Optimization")
                checkBox("Assertions")
                checkBox("IR")
                checkBox("Assertions")
                comboBox(arrayOf("1.8", "11")).component.toolTipText = "Target"
            }

            row {
                val chooserDescriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor()
                val chooseBtn2 = textFieldWithBrowseButton(fileChooserDescriptor = chooserDescriptor)
                chooseBtn2.component.isEditable = false
                chooseBtn2.text("choose compiler 2")

                checkBox("Inline")
                checkBox("Optimization")
                checkBox("Assertions")
                checkBox("IR")
                checkBox("Assertions")
                comboBox(arrayOf("1.8", "11")).component.toolTipText = "Target"
            }

            row {
                cell(diffPanel.component)
                    .horizontalAlign(HorizontalAlign.FILL)
                    .verticalAlign(VerticalAlign.FILL)
            }.resizableRow()
        }
    }

    private fun buildRequest(title1: String, title2: String, text1: String, text2: String): ContentDiffRequest {
        val content1 = DiffContentFactory.getInstance().create(text1)
        val content2 = DiffContentFactory.getInstance().create(text2)
        return SimpleDiffRequest("Window Title", content1, content2, title1, title2)
    }
}