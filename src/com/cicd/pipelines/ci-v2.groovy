@Library("my-global-libs@map-config") _
import com.cicd.libs.*

pipeline {
    agent { label 'master' }
    options {
        skipDefaultCheckout(true)
        buildDiscarder(logRotator(numToKeepStr: '100', daysToKeepStr: '1'))
        ansiColor('xterm')
    }
    parameters {
        string(trim: true, name: 'CODE_PROJECT_URL',defaultValue: 'https://src.local.xx.com/ops/cicd-test-project.git',  description: '【必须】代码工程的gitlab url')
        string(trim: true, name: 'CODE_BRANCH',     defaultValue: 'develop',  description: '【必须】需要构建的分支名字')
        string(trim: true, name: 'CODE_COMMIT_SHA', defaultValue: '5c64393c49bdb46127127316afedd429524e4c84',  description: '【非必须】需要构建的提交（哈希），默认取最新的提交')
        string(trim: true, name: 'APP_OF_PROJECT',  defaultValue: 'cicd-test-project',  description: '【非必须】指定 Gitlab Project 某个APP 进行构建，留空则表似与工程名字相同')
    }
    environment {
        // 用于日志输出的辅助变量，带xterm 标准的颜色（需要ansiColor('xterm')插件的支持）
        _debug  = "[\033[1;34m  DEBUG\033[0m]"
        _error  = "[\033[1;31m  ERROR\033[0m]"
        _warn   = "[\033[1;33mWARNING\033[0m]"
        _info   = "[\033[1;34m   INFO\033[0m]"
    }
    stages {
        stage('流水线环境初始化') { steps{ script{
            // (1) 导入流水线配置（详细信息参考：vars/setUpPipelineRunTimeEnv.groovy）
            setUpPipelineRunTimeEnv.call()
            // (2) 解析代码参数（详细信息参考：libs/codeInfoParser.groovy）
            new codeInfoParser().call()
            // (3) 导入应用配置
            appConfigs = cmdbAPPConfig.get(CODE_GROUP, CODE_PROJECT, CODE_BRANCH, APP_OF_PROJECT)
            // (4) 获取 profile 对应的环境信息
            appConfigs = cmdbProfileConfig.fillProfile(appConfigs)
            // (5) 解析关联的通讯录用于流水线 relatedGitlabUsers（libs/contactParser.groovy）
            relatedGitlabUsers = readJSON text: new contactParser().get(env.CODE_PROJECT_URL,env.CODE_COMMIT_SHA)
            // (6) 准备好代码检出路径和构建路径（libs/codeManager.groovy）
            _codeManager = new codeManager()
            _codeManager.prepare(appConfigs,env.CODE_BRANCH)
        }}}

        stage('检出代码') { steps{ script{
            // 详情参考： libs/codeManager.groovy
            _codeManager.checkout(appConfigs,env.CODE_BRANCH)
            // 获取版本细节（版本、commit message），填充到 appConfigs 中去
            appConfigs = _codeManager.fetch_version(appConfigs)
        }}}

        stage('发布源码Release'){
        when { environment name: "CODE_BRANCH", value: "master" }
        steps{ script{
            // master 分支不参与构建，仅用作新特性分支的源分支
            // master 分支上的大版本变化都需要发布源码 Releases，我们并不需要打源码包，只需要创建release 分支即可
            // 后续该版本上的热修复直接在相应的release 分支上操作，上线后合并回master 分支
            // * 这里的具体逻辑需要解决每个公司具体的分支管理规范来适配 *
            _codeManager.release_source_code(appConfigs)
        }}}

        stage('编译源码'){ steps{ script{
            // TODO
            // 1、将构建好的工件放入 nexus
            // 2、交叉编译（ARM 版本应用）
            appConfigs = new codeBuilder().build(appConfigs)
            assert appConfigs : "${_error} - 所有APP 都构建失败了，终止后续步骤"
        }}}

        stage('构建容器镜像'){ steps{ script{
            // TODO
            // 1、加入条件：容器化的应用才执行此步骤（兼容没有容器化的应用）
            // 2、构建ARM 版本镜像
            appConfigs = new imageBuilder().build(appConfigs)
            assert appConfigs : "${_error} - 所有APP 的镜像都构建失败了，终止后续步骤"
        }}}

        stage('渲染Chart包'){ steps{ script{
            // TODO
            // 1、加入条件：容器化的应用才执行此步骤（兼容没有容器化的应用）
            appConfigs = new chartBuilder().build(appConfigs)
            assert appConfigs : "${_error} - 所有APP 的Chart都构建失败了，终止后续步骤"
        }}}

        stage('触发CD 流水线'){ steps{ script{
            new cdPipelineTrigger().call(appConfigs)
        }}}
    }//stages


    // post documents: https://www.jenkins.io/zh/doc/pipeline/tour/post/
    post {
        failure {
            script {
                env.jenkins_job_number = currentBuild.number
                error("报错啦:")
            }
        }
    }//post
}// end of pipeline
