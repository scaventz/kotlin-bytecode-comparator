package com.scaventz.window

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContext
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.base.HighlightPolicy
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

class ComparatorForm(val project: Project) {
    val panel: JPanel

    private var differ = SimpleDiffViewer(Context, buildRequest("compiler 1", "compiler 1", "", "")).apply { init() }
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
                            // how to update content of request of differ?
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
                cell(differ.component).horizontalAlign(HorizontalAlign.FILL)
            }
        }
    }

    private fun buildRequest(title1: String, title2: String, text1: String, text2: String): ContentDiffRequest {
        val content1 = DiffContentFactory.getInstance().create(text1)
        val content2 = DiffContentFactory.getInstance().create(text2)
        return SimpleDiffRequest("Window Title", content1, content2, title1, title2)
    }
}

object Context : DiffContext() {
    init {
        val settings = TextDiffSettingsHolder.TextDiffSettings()
        settings.highlightPolicy = HighlightPolicy.BY_WORD
        settings.ignorePolicy = IgnorePolicy.IGNORE_WHITESPACES
        settings.contextRange = 5
        settings.isExpandByDefault = true
        settings.isEnableSyncScroll = true
        putUserData(TextDiffSettingsHolder.TextDiffSettings.KEY, settings)
    }

    override fun isFocusedInWindow() = false
    override fun requestFocusInWindow() {}
    override fun getProject(): Project? = null
    override fun isWindowFocused() = false
}



