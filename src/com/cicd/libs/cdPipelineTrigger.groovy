package com.cicd.libs


def _doCallCdForAppProfile(appConfig,profile){
    if(profile.appConfigurations.autoDeploy){
        // 
        build( // 文档地址： https://www.jenkins.io/doc/pipeline/steps/pipeline-build-step/ 
            job: "cd-pipeline",
            wait: false,
            parameters: [
                [ $class: 'StringParameterValue', name: 'CODE_GROUP',      value: "${appConfig.group}",    ],
                [ $class: 'StringParameterValue', name: 'CODE_PROJECT',    value: "${appConfig.project}",  ],
                [ $class: 'StringParameterValue', name: 'APP_NAME',        value: "${appConfig.name}",     ],
                [ $class: 'StringParameterValue', name: 'CODE_COMMIT_SHA', value: "${appConfig.commitSha}",],
                [ $class: 'StringParameterValue', name: 'APP_VERSION',     value: "${appConfig.version}",  ],
                [ $class: 'StringParameterValue', name: 'DEPLOY_ENV',      value: "${profile.name}",       ]
            ]
        )
    }else{
        println "${_info} - APP:${appConfig.name} 在环境${profile.name} 下无需自动部署"
    }
}

def _doCallCdForApp(appConfig){
    appConfig = appConfig.build_profiles.collect { profile -> _buildChartWithProfile(appConfig,profile) }
    return appConfig
}

def call(allConfigs){
    def successList = []
    for(def appConfig:allConfigs){
        try{
            successList.add(_doCallCdForApp(appConfig))
        }catch(error_msg){
            echo "${_error} - 为APP ${appConfig.name} 调用CD 流水线失败:${error_msg}"
            continue
        }
    }
    return successList
}