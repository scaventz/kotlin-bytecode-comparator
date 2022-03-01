package com.scaventz.services

import com.intellij.openapi.project.Project
import com.scaventz.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
