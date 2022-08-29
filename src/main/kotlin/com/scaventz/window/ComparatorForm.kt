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
import com.intellij.openapi.vfs.VirtualFile
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
import com.scaventz.data.Decompiled
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.createTempDirectory

open class ComparatorForm(private val project: Project) {
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

    private val log = Logger.getInstance(this::class.java)

    init {
        panel = panel {
            row {
                val chooserDescriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor()
                browseCell1 = textFieldWithBrowseButton(fileChooserDescriptor = chooserDescriptor)
                browseCell1.component.isEditable = false
                browseCell1.text("compiler dir")
                browseCell1.component.textField.document.addDocumentListener(
                    MyDocumentAdapter(browseCell1, kotlinc1, this@ComparatorForm)
                )
                checkBox("Inline").bindSelected(kotlinc1.inline)
                checkBox("Optimization").bindSelected(kotlinc1.optimization)
                checkBox("Assertions").bindSelected(kotlinc1.assertions).enabled(false)
                checkBox("JVM_IR").bindSelected(kotlinc1.jvmIR)
                checkBox("FIR").bindSelected(kotlinc1.fir)
                comboBox(listOf("1.8", "11")).apply {
                    component.toolTipText = "Target (Not supported yet)"
                }.enabled(false)
            }

            row {
                val chooserDescriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor()
                browseCell2 = textFieldWithBrowseButton(fileChooserDescriptor = chooserDescriptor)
                browseCell2.component.isEditable = false
                browseCell2.text("compiler dir")
                browseCell2.component.textField.document.addDocumentListener(
                    MyDocumentAdapter(browseCell2, kotlinc2, this@ComparatorForm)
                )
                checkBox("Inline").bindSelected(kotlinc2.inline)
                checkBox("Optimization").bindSelected(kotlinc2.optimization)
                checkBox("Assertions").bindSelected(kotlinc2.assertions).enabled(false)
                checkBox("JVM_IR").bindSelected(kotlinc2.jvmIR)
                checkBox("FIR").bindSelected(kotlinc2.fir)
                comboBox(listOf("1.8", "11")).apply {
                    component.toolTipText = "Target (Not supported yet)"
                }.enabled(false)

                compareBtn = button("Compile") {
                    compileAndCompare()
                }
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

    private fun compileAndCompare() {
        var result1: Decompiled? = null
        var result2: Decompiled? = null
        var request: ContentDiffRequest? = null
        val editorManager = FileEditorManager.getInstance(project)
        val editor = editorManager.selectedTextEditor.castSafelyTo<EditorEx>() ?: throw RuntimeException()
        val psi = getKtFile(editor.virtualFile)
        val relativePath = psi.packageFqName.asString().replace('.', '/')
        Thread {
            try {
                compareBtn.enabled(false)
                compareBtn.component.text = "Compiling..."

                runBlocking {
                    log.info("src: ${psi.text}")
                    val tempDir = createTempDirectory("bytecode_comparator").toFile()

                    val outputDir1 = File(tempDir, "compiler1")
                    val outputDir2 = File(tempDir, "compiler2")

                    launch {
                        kotlinc1.compile(psi, outputDir1)
                        val map1 = kotlinc1.decompile(File(outputDir1, relativePath))
                        assert(map1.isNotEmpty()) {
                            "Doesn't find any class file under ${File(outputDir1, relativePath)}"
                        }
                        val decompiled1 = map1.map { it.value }.reduce { acc, s -> acc + "\n\n" + s }
                        result1 = Decompiled(decompiled1, kotlinc1.version)
                    }

                    launch {
                        kotlinc2.compile(psi, outputDir2)
                        val map2 = kotlinc2.decompile(File(outputDir2, relativePath))
                        assert(map2.isNotEmpty()) {
                            "Doesn't find any class file under ${File(outputDir2, relativePath)}"
                        }
                        val decompiled2 = map2.map { it.value }.reduce { acc, s -> acc + "\n\n" + s }
                        result2 = Decompiled(decompiled2, kotlinc2.version)
                    }
                }
                request = buildRequest(
                    result1?.version ?: "failed",
                    result2?.version ?: "failed",
                    result1?.text ?: "failed",
                    result2?.text ?: "failed"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                request = buildRequest(
                    "failed",
                    "failed",
                    e.stackTraceToString(),
                    e.stackTraceToString()
                )
            } finally {
                diffPanel.setRequest(request)
                enableButtonIfPossible()
            }
        }.start()
    }

    // TODO - Investigate why sometimes updated source code doesn't reflect in PSI
    private fun getKtFile(virtualFile: VirtualFile): KtFile {
        val psiManager = PsiManager.getInstance(project)
        val psi = psiManager.findFile(virtualFile) ?: throw RuntimeException("virtualFile $virtualFile is null")
        if (psi is KtFile) {
            return psi
        } else {
            throw RuntimeException("only kotlin file supported")
        }
    }

    fun enableButtonIfPossible() {
        if (kotlinc1.bin != null && kotlinc2.bin != null) {
            compareBtn.enabled(true)
            compareBtn.component.text = "Compile"
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
