package com.cicd.libs


def _do_prepare(appConfig,appCodeBranch){
    // 为了加速代码检出，我们将代码固定检出到一个目录，流水线每次触发，都只做pull，而不做全量的checkout
    // 一个gitlab 工程可能同时有多个分支触发构建，因此每个分支都要有独立的检出路径
    // 构建过程会产生很多文件，为了不干扰检出目录，我们实际构建时会将代码拷贝到工作空间（workspace）
    // *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*
    // 代码缓存目录
    def appCodeCaheDir = new File("${env.CI_CODE_CACHE_BASE}/${appConfig.group}/${appConfig.project}/${appConfig.name}")
    appCodeCaheDir.mkdirs()
    println "${_debug} - APP代码缓存路径：${appCodeCaheDir.path}"
    def appCodeBranchCacheDir = "${appCodeCaheDir.path}/${appCodeBranch}"
    println "${_debug} - APP 代码分支缓存路径： ${appCodeBranchCacheDir}"

    def appCodeBranchBuildDir = new File("${WORKSPACE}/${appConfig.name}-${CICD_INIT_TIME}")
    appCodeBranchBuildDir.mkdirs()
    println "${_debug} - 代码构建路径: ${appCodeBranchBuildDir.path}"
    appConfig["appCodeBranchCacheDir"] = appCodeBranchCacheDir
    appConfig["appCodeBranchBuildDir"] = appCodeBranchBuildDir.path
}

def prepare(appConfigs,appCodeBranch){
    appConfigs = appConfigs.collect { appConfig -> _do_prepare(appConfig,appCodeBranch) }
    return appConfigs
}


def _do_checkout(appConfig,appCodeBranch){
    Integer tryCount = 0
    def checkoutSuccess = false
    def thisFilePath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath()
    def checkoutScript = "${thisFilePath.split('/src/')[0]}/resources/com/cicd/checkout.sh"
    while(tryCount<5) {
        println "${_debug} - 第 ${tryCount + 1} 次尝试检出代码:${CODE_PROJECT_URL}，branch：${appCodeBranch}"
        try {
            def gitUrlList = CODE_PROJECT_URL.split("://")
            def gitOauthUrl = "${gitUrlList[0]}://oauth2:${CI_GITLAB_TOKEN}@${gitUrlList[1]}"
            sh "/usr/bin/bash ${checkoutScript} \
                              ${gitOauthUrl}    \
                              ${appConfig.appCodeBranchBuildDir} \
                              ${appConfig.appCodeBranchCacheDir} \
                              ${tryCount}"
            println "${_debug} - 检出代码成功"
            checkoutSuccess = true
            break
        } catch(error_msg) {
            sleep(10)
            tryCount++
        }
    }
    assert checkoutSuccess : "${_error} - 检出代码失败5次，请联系运维确认"
}

def checkout(appConfigs,appCodeBranch){
    appConfigs.each { appConfig -> _do_checkout(appConfig,appCodeBranch) }
}


def _fetch_common_version(versionFile){
    def versionCode = sh(returnStdout:true,script:"""
          set +x
          grep module_version ${versionFile}\
          | sed -r 's/module_version[ ]*=[ ]*(.*)/\\1/g'
        """).trim()
    return versionCode
}

def _fetch_java_version(versionFile){
    def thisFilePath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath()
    def pyScript = "${_thisFilePath.split('/src/')[0]}/resources/com/cicd/getPOMVersion.py"
    def versionCode = sh(returnStdout:true,script:""" python3 ${pyScript} ${versionFile} """).trim()
    return versionCode
}

def _fetch_js_version(versionFile){
    def versionCode = sh(returnStdout:true,script:"""
           set +x
           grep '"version":' ${versionFile} \
           | sed -r 's/"version": "([0-9]+\\.[0-9]+\\.[0-9]+(-[0-9a-zA-Z-]+){0,1})",/\\1/g' \
           | grep -v version
        """).trim()
    return versionCode
}

def _fetch_commit_message(appConfig){
    def commitMessage = sh(returnStdout:true,script:"""
                            cd ${appConfig.appCodeBranchCacheDir}
                            git log --oneline |head -n 1
                        """).trim()
    println "${_debug} - APP ${appConfig.name} 的提交信息为： ${commitMessage}"
    return commitMessage
}

def _fetch_version(appConfig,versionFile){
    def versionCode = ""
    if(versionFile.indexOf('version.txt') != -1){
        versionCode = _fetch_common_version(versionFile)

    }else if(versionFile.indexOf('pom.xml') != -1){
        versionCode = _fetch_java_version(versionFile)

    }else if(versionFile.indexOf('package.json') != -1){
        versionCode = _fetch_js_version(versionFile)

    }else{
        error("${_error} - 不支持的版本记录文件类型")
    }
    assert versionCode : "${_error} - 为${appConfig.name} 获取代码版本号失败"
    def _res = sh(returnStdout:true,script:"""echo ${versionCode}|grep -E 'v{0,1}([0-9]+.){2}[0-9]+'|wc -l""").trim()
    assert _res != "0" : "${_error} - APP ${appConfig.name} 的版本号格式不正确，请联系运维确认"
    println "${_debug} - 为APP ${appConfig.name} 提取到版本号： ${versionCode}"
    return versionCode
}

def _do_fetch_version(appConfig){
    def versionFile = "${appConfig.appCodeBranchCacheDir}/${appConfig.version_file}"
    appConfig["version"] = _fetch_version(appConfig,versionFile)
    appConfig["commitMessage"] = _fetch_commit_message(appConfig)
    appConfig["codeProjectUrl"] = env.CODE_PROJECT_URL
    appConfig["codeBranch"] = env.CODE_BRANCH
    appConfig["codeGroup"] = env.CODE_GROUP
    appConfig["codeProject"] = env.CODE_PROJECT
    appConfig["codeCommitSha"] = env.CODE_COMMIT_SHA
    appConfig["codeCommitShaShort"] = env.CODE_COMMIT_SHA_SHORT
    appConfig["codeCheckoutPoint"] = env.CODE_CHECKOUT_POINT
    appConfig["codeCommiters"] = env.CODE_COMMITERS
    appConfig["ciInitTime"] = env.CICD_INIT_TIME
    return appConfig
}

def fetch_version(appConfigs){
    appConfigs = appConfigs.collect { appConfig -> _do_fetch_version(appConfig) }
    echo "${_debug} - 补充版本细节后：${appConfigs}"
    return appConfigs
}



def _do_release_source_code(appConfig){
    def pyScript = "${CICD_TOOL_SCRIPTS_DIR}/gitCreateReleaseBranch.py"
    sh(returnStdout:false, script:"""
        set +x
        source ~/.bashrc
        pyenv local 3.8.0
        set -x
        python3 ${pyScript} \
        -p '${CODE_PROJECT_URL}' \
        -b '${appConfig.name}%Release-${appConfig.version}' \
        -m '${CODE_COMMIT_SHA}' 
    """)
}

def release_source_code(appConfigs){
    appConfigs.collect { appConfig -> _do_release_source_code(appConfig) }
}