package com.cicd.libs

def call(){

// 我们需要解析出如下变量，以便后续流水线执行构建
// *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*
// 
//     1、 CODE_PROJECT_URL
//     2、 CODE_BRANCH
//     3、 CODE_GROUP
//     4、 CODE_PROJECT
//     5、 CODE_COMMIT_SHA, CODE_COMMIT_SHA_SHORT
//     6、 CODE_CHECKOUT_POINT
//     7、 CODE_COMMITERS
//     8、 APP_OF_PROJECT


// 解析 1、 CODE_PROJECT_URL  2、 CODE_BRANCH 
// *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*
// 
//    env.gitlabXXXXX 是gitlab 的webhook 触发jenkins 流水线时会注入的参数,
//    一旦这个参数有值，就表示流水线时gitlab 触发的，而不是认为点击执行的 
// 
env.CODE_PROJECT_URL = env.gitlabSourceRepoHttpUrl ? env.gitlabSourceRepoHttpUrl : env.CODE_PROJECT_URL
env.CODE_BRANCH = env.gitlabTargetBranch ? env.gitlabTargetBranch : env.CODE_BRANCH
if(!env.CODE_BRANCH || !env.CODE_PROJECT_URL){
    error("${_debug} - 无法确定代码地址或者分支")
}

// 解析 3、 CODE_GROUP 4、 CODE_PROJECT
// *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*
// 
//     根据 CODE_PROJECT_URL 提取代码组 和 工程名字
//     比如：CODE_PROJECT_URL = https://src.local.xx.com/ops/test1/third-level-group/test32.git
//     则 CODE_GROUP = ops.test1.third-level-group
// 
env._path = CODE_PROJECT_URL.minus(CI_GITLAB_URL).minus('/') // => ops/test1/third-level-group/test32.git
env.CODE_GROUP = sh(script:"echo \${_path%/*}|tr '/' '.' ",returnStdout: true).trim()
env.CODE_PROJECT = sh(script:"echo \${_path##*/}|sed -r 's/(.*)\\.git\$/\\1/'",returnStdout: true).trim()
// 受沙箱影响，replaceAll 不让用，改用上述shell
//env.CODE_GROUP  = _path.replaceAll(~'((/[a-zA-Z0-9]+)+)/[a-zA-Z0-9]+.git', '$1').minus('/').replaceAll('/','.')
//env.CODE_PROJECT   = _path.replaceAll(~'.*/([a-zA-Z0-9]+).git','$1')
echo "${_debug} - git repo url path: ${_path}; group sequence: ${CODE_GROUP}; project name: ${CODE_PROJECT}"


// 解析 5、 CODE_COMMIT_SHA  6、 CODE_CHECKOUT_POINT
// *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*
// 
//    如果是gitlab 触发，则从 env.gitlabAfter 中获取commit sha
//    否则从流水线参数 env.CODE_COMMIT_SHA 中获取（不一定有）
//    如果 env.CODE_COMMIT_SHA 为空，则从gitlab 的制定分支上获取最新的commit sha
// 
// 【注意】
//    如果最终无法得到具体的commit sha, 流水线将抛错退出，因为这个sha 很重要
// 
env.CODE_COMMIT_SHA = env.gitlabAfter ? env.gitlabAfter : env.CODE_COMMIT_SHA
if(!env.CODE_COMMIT_SHA){
    try {
        env.CODE_COMMIT_SHA = sh(returnStdout: true, script: """
            set +x
            source ~/.bashrc
            pyenv local 3.8.0
            set -x
            python3 \${CICD_TOOL_SCRIPTS_DIR}/gitGetLastCommitSHA.py  \
            -g "${CODE_GROUP}"                                  \
            -p "${CODE_PROJECT}"                                \
            -b "${CODE_BRANCH}"
        """).trim()
    }catch(error_msg){
        error("${_warn} - get commiter failed")
    }
}
env.CODE_COMMIT_SHA_SHORT = CODE_COMMIT_SHA[0..6]
// 我们统一以 CODE_COMMIT_SHA 为检出点
env.CODE_CHECKOUT_POINT = env.CODE_COMMIT_SHA
echo "${_debug} - CODE_COMMIT_SHA: ${CODE_COMMIT_SHA}; CODE_CHECKOUT_POINT： ${CODE_CHECKOUT_POINT}"



// 提取代码的实际提交者 6、 CODE_COMMITERS
// *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*
// 
//      如果是人为触发的流水线，流水线无法获取到 gitlabUserName，
//      就要根据分支和 CODE_COMMIT_SHA 去查询谁提交的代码
// 
env.CODE_COMMITERS = env.gitlabUserName
if(!env.CODE_COMMITERS){
    try {
        env.CODE_COMMITERS = sh(returnStdout:true, script: """
            set +x
            source ~/.bashrc
            pyenv local 3.8.0
            set -x
            python3 \${CICD_TOOL_SCRIPTS_DIR}/gitGetCommitter.py  \
            -p "\${CODE_PROJECT}"                           \
            -g "\${CODE_GROUP%%.*}"                         \
            -m "\${CODE_COMMIT_SHA}"
        """).trim()
    }catch(error_msg){
        echo "${_warn} - get commiter failed"
    }
}
echo "${_debug} - code commiter: ${CODE_COMMITERS}"


// 解析需要构建的模块名清单 7、 APP_OF_PROJECT
// *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*
// 
//     如果流水线运行时没有为 APP_OF_PROJECT 赋值，需要根据分支规则来判断
//     这很大程度上取决于具体的分支管理规范
// 
//     根据我们的规范：
//     一、如果 分支名字中存在 %Release- 或者 %Project- 字样，则认为是单模块分支，
//     比如如下分支都是单模块分支：
//        module_name_1%Release-v1.1.x <--------------release 稳定分支
//        module_name_2%some_custom%Project-v1.4.x <--交付项目分支
//     对于单模块分支，当次流水线只负责构建单一模块，将分支以 % 切割，取最前面的部分
// 
//     二、其他非单模块的分支，比如dev 分支，则默认构建分支下的所有模块，
//     此时，我们为 APP_OF_PROJECT 赋值 "ALL"
// 
if(!env.APP_OF_PROJECT) {
    if(CODE_BRANCH.indexOf("%Release-")!= -1 || CODE_BRANCH.indexOf("%Project-")!= -1) {
        env.APP_OF_PROJECT = CODE_BRANCH.split('%')[0]
    }else{
        env.APP_OF_PROJECT = "ALL"
    }
}
echo "${_debug} - APP_OF_PROJECT: ${APP_OF_PROJECT}"


}