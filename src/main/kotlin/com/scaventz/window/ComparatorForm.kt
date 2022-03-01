package com.scaventz.window

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.base.HighlightPolicy
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
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
                            diffPanel.setRequest(buildRequest("compiler1", "compiler2", "123", "124"))
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