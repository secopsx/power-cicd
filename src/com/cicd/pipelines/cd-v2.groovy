@Library("my-global-libs@map-config") _
import com.cicd.libs.*

pipeline {
    agent { label 'master' }
    options {
        skipDefaultCheckout(true)
        buildDiscarder(logRotator(numToKeepStr: '10', daysToKeepStr: '1'))
        ansiColor('xterm')
    }
    parameters {
        string(trim: true, name: 'CODE_GROUP',          defaultValue: 'ops', description: '【必须】模块所在gitlab 组，若是嵌套组，则用符号点串接各级组名，比如:group1.group2.group3')
        string(trim: true, name: 'CODE_PROJECT',        defaultValue: 'cicd-test-project', description: '【必须】代码在gitlab 上的工程的名字')
        string(trim: true, name: 'CODE_COMMIT_SHA',     defaultValue: '5c64393c49bdb46127127316afedd429524e4c84', description: '【必须】需要构建的提交哈希')
        string(trim: true, name: 'APP_NAME',            defaultValue: 'cicd-test-project', description: '【非必须】应用名字，若为空，则同 CODE_PROJECT')
        string(trim: true, name: 'APP_VERSION',         defaultValue: '1.1.0', description: '【必须】APP的版本号（不要v）')
        string(trim: true, name: 'DEPLOY_ENV',          defaultValue: 'dev', description: '【必须】待部署得环境，也就是runtime profile')
        string(trim: true, name: 'ENVIRONMENT_VARIABLE',defaultValue: '', description: '【非必须】传递额外得环境变量给APP，格式： key1=value1,key2=value2')
    }
    environment {
        // 用于日志输出的辅助变量，带xterm 标准的颜色
        _debug  = "[\033[1;34m  DEBUG\033[0m]"
        _error  = "[\033[1;31m  ERROR\033[0m]"
        _warn   = "[\033[1;33mWARNING\033[0m]"
        _info   = "[\033[1;34m   INFO\033[0m]"
    }
    stages {
        stage('参数解析'){ steps{ script{
            // (1) 设置流水线运行参数
            setUpPipelineRunTimeEnv.call()
            // (2) 解析环境参数和应用的CD 配置
            deployTools = new cdTools()
            profileConfig = deployTools.getProfile(env.CODE_GROUP, env.CODE_PROJECT, env.APP_NAME, env.DEPLOY_ENV)
            profileConfig.appConfigurations.putAll([
                "version": env.APP_VERSION,
                "codeCommitSha": env.CODE_COMMIT_SHA,
                "codeCommitShaShort": env.CODE_COMMIT_SHA[0..6]
            ])
            // (3) 解析 ENVIRONMENT_VARIABLE
            profileConfig["extraEnvironmentVariables"] = deployTools.genextraEnvironmentVariables(env.ENVIRONMENT_VARIABLE)
            println "${_info} - 应用 ${env.CODE_GROUP}:${env.APP_NAME} 的部署参数为：${profileConfig}"

        }}} 

        stage('执行部署'){ steps{ script{
            // (1) 调用部署驱动执行部署
            deployTools.deploy(profileConfig)

        }}}
    }

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