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
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.castSafelyTo
import com.scaventz.services.Kotlinc
import java.io.File
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import com.intellij.ui.dsl.builder.bindSelected
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.event.ActionEvent
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory

@Suppress("UnstableApiUsage")
open class ComparatorForm(project: Project) {
    val panel: JPanel

    private val diffPanel = DiffManager.getInstance()
        .createRequestPanel(null, Disposer.newDisposable(), null).apply {
            putContextHints(DiffUserDataKeysEx.FORCE_DIFF_TOOL, SimpleDiffTool.INSTANCE)
        }

    private lateinit var browseCell1: Cell<TextFieldWithBrowseButton>
    private lateinit var browseCell2: Cell<TextFieldWithBrowseButton>
    private lateinit var compareBtn: Cell<JButton>
    private val kotlinc1 = Kotlinc()
    private val kotlinc2 = Kotlinc()

    private val executor = Executors.newSingleThreadExecutor()
    private val editorManager = FileEditorManager.getInstance(project)
    private val psiManager = PsiManager.getInstance(project)
    private val log = Logger.getInstance(this::class.java)

    private val compareBtnListener: (event: ActionEvent) -> Unit

    init {
        compareBtnListener = buildCompareBtnListener()

        panel = panel {
            row {
                val chooserDescriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor()
                browseCell1 = textFieldWithBrowseButton(fileChooserDescriptor = chooserDescriptor)
                browseCell1.component.isEditable = false
                browseCell1.text("choose compiler 1")
                browseCell1.component.textField.document.addDocumentListener(
                    MyDocumentAdapter(browseCell1, kotlinc1, this@ComparatorForm)
                )
                checkBox("Inline").bindSelected(kotlinc1.inline).enabled(false)
                checkBox("Optimization").bindSelected(kotlinc1.optimization).enabled(false)
                checkBox("Assertions").bindSelected(kotlinc1.assertions).enabled(false)
                checkBox("IR").bindSelected(kotlinc1.ir)
                comboBox(arrayOf("1.8", "11")).apply {
                    component.toolTipText = "Target (Not supported yet)"
                }.enabled(false)
            }

            row {
                val chooserDescriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor()
                browseCell2 = textFieldWithBrowseButton(fileChooserDescriptor = chooserDescriptor)
                browseCell2.component.isEditable = false
                browseCell2.text("choose compiler 1")
                browseCell2.component.textField.document.addDocumentListener(
                    MyDocumentAdapter(browseCell2, kotlinc2, this@ComparatorForm)
                )
                checkBox("Inline").bindSelected(kotlinc2.inline).enabled(false)
                checkBox("Optimization").bindSelected(kotlinc2.optimization).enabled(false)
                checkBox("Assertions").bindSelected(kotlinc2.assertions).enabled(false)
                checkBox("IR").bindSelected(kotlinc2.ir)
                comboBox(arrayOf("1.8", "11")).apply {
                    component.toolTipText = "Target (Not supported yet)"
                }.enabled(false)

                compareBtn = button("Compile And Compare", compareBtnListener)
                compareBtn.enabled(false)
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

    private fun buildCompareBtnListener(): (event: ActionEvent) -> Unit {
        return mark@{
            val editor = editorManager.selectedTextEditor.castSafelyTo<EditorEx>() ?: return@mark
            val psi = psiManager.findFile(editor.virtualFile) ?: return@mark
            compareBtn.enabled(false)
            compareBtn.component.text = "Compiling..."
            executor.execute {
                log.info("src: ${psi.text}")
                if (psi.text == null || psi.text.isEmpty()) return@execute

                val tempDir = createTempDirectory("bytecode_comparator").toFile()
                val outputDir1 = File(tempDir, "compiler1")
                val outputDir2 = File(tempDir, "compiler2")
                var decompiled1: String? = null
                var decompiled2: String? = null

                runBlocking {
                    launch {
                        log.info("compiling start in thread: ${Thread.currentThread().name}")
                        kotlinc1.compile(psi, outputDir1)
                        log.info("compiling end in thread: ${Thread.currentThread().name}")
                        val map1 = kotlinc1.decompile(outputDir1, psi.virtualFile.path)
                        decompiled1 = map1.map { it.value }.reduce { acc, s -> acc + "\n\n" + s }
                    }

                    launch {
                        log.info("compiling start in thread: ${Thread.currentThread().name}")
                        kotlinc2.compile(psi, outputDir2)
                        log.info("compiling end in thread: ${Thread.currentThread().name}")
                        val map2 = kotlinc2.decompile(outputDir2, psi.virtualFile.path)
                        decompiled2 = map2.map { it.value }.reduce { acc, s -> acc + "\n\n" + s }
                    }
                }

                // update panel
                val request = buildRequest(
                    title1 = kotlinc1.version,
                    title2 = kotlinc2.version,
                    decompiled1!!,
                    decompiled2!!
                )
                diffPanel.setRequest(request)
                enableButtonIfPossible()
            }
        }
    }

    fun enableButtonIfPossible() {
        if (kotlinc1.bin != null && kotlinc2.bin != null) {
            compareBtn.enabled(true)
            compareBtn.component.text = "Compile And Compare"
        }
    }

    internal class MyDocumentAdapter(
        private val browseCell: Cell<TextFieldWithBrowseButton>,
        private var kotlinc: Kotlinc,
        private val form: ComparatorForm
    ) : DocumentAdapter() {
        private val log = Logger.getInstance(this::class.java)

        override fun textChanged(e: DocumentEvent) {
            val path = browseCell.component.text
            log.info("path: $path")
            if (path.isEmpty()) return

            val bin = File(path).listFiles()?.singleOrNull {
                it.name == "bin" && it.isDirectory
            } ?: return

            kotlinc.bin = bin
            form.enableButtonIfPossible()
        }
    }
}
