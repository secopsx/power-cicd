package com.cicd.libs


def _buildAppWithProfile(appConfig,profile){
    def profileBuildRootDir = "${appConfig.appCodeBranchBuildDir}/${profile.name}"
    println "${_debug} - 开始构建 APP ${appConfig.name}，\nprofile name: ${profile.name}\nprofile: ${profile}"
    sh """
    set +x
    source /etc/profile
    set -x
    mkdir -p ${profileBuildRootDir}
    cd ${profileBuildRootDir}
    cp ${appConfig.appCodeBranchCacheDir}/* .
    cd ${appConfig.build_dir}
    pwd
    APP_BUILD_PROFILE=${profile.name}
    ${appConfig.build_command}
    """
    appConfig["appAbsoluteBuildDir"] = "${profileBuildRootDir}/${appConfig.build_dir}"
    return appConfig
}

def _buildApp(appConfig){
    appConfig.build_profiles.collect { profile -> _buildAppWithProfile(appConfig,profile) }
}

def build(allConfigs){
    def successList = []
    // allConfigs.each { appConfig -> _buildApp(appConfig) }
    // 并发构建时，构建过程的输出存在相互干扰，还是一个一个来
    for(def appConfig:allConfigs){
        try{
            _buildApp(appConfig)
            successList.add(appConfig)
        }catch(error_msg){
            echo "${_error} - 构建APP ${appConfig.name} 失败"
            continue
        }
    }
    return successList
}