package com.cicd.libs

// 
// 解析代码关联的通讯录
// *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*
// 什么是代码关联的通讯录？
//    1、代码的合并者
//    2、代码合并时，源分支和目标分支上的最后提交者
//    3、APP 的运维负责人
// 
// 参数：
//    1、codeCheckOutUrl  ：代码的project 的url
//    2、codeCommitSHA：代码的commit sha
// 
// 返回：
//    返回 List[map] 类型
//    [
//       ["name":"xiaoming","id":"","phone":"","email":"xx@yy.com"],
//    ]
// 

def get(codeCheckOutUrl,codeCommitSHA){
    def pyScript = "${CICD_TOOL_SCRIPTS_DIR}/gitGetCommitter.py"
    // echo "${_pyScript}"
    def users = sh(returnStdout:true,script:"""
        set +x
        source ~/.bashrc
        pyenv local 3.8.0
        set -x
        python3 ${pyScript} -p ${codeCheckOutUrl} -c ${codeCommitSHA}
    """).trim()
    return users
}