package com.cicd.libs


def _buildImageWithProfile(appConfig,profile){

    def imageTag = "${profile.envConfigurations.harborAddress}/"+
                   "${profile.envConfigurations.harborProject}/"+
                   "${appConfig.name}:"+
                   "${appConfig.version}-sha-${CODE_COMMIT_SHA_SHORT}-${profile.name}"

    def dockerfile = "${CI_DOCKERFILES_DIR}/${appConfig.dockerfile}"
    def startupScript = "${CI_APP_STARTUP_SCRIPTS_DIR}/${appConfig.startup_script}"
    def profileBuildRootDir = "${appConfig.appCodeBranchBuildDir}/${profile.name}"

    echo "${_debug} - 开始为APP ${appConfig.name} 构建镜像: \n"+
         "    |--- Profile Name  : ${profile.name} \n"+
         "    |--- Profile Detail: ${profile} \n"+
         "    |--- ImageName     : ${imageTag} \n"+
         "    |--- Dockerfile    : ${dockerfile} \n"+
         "    |--- startupScript : ${startupScript}"

    for(String buildResult : appConfig.build_results.split(",")){
        sh """
            cd ${profileBuildRootDir}
            cd ${appConfig.build_dir}
            pwd
            mkdir -p image_build_dir/${appConfig.name}
            cp -ar ${buildResult} image_build_dir/${appConfig.name}/
        """
    }
    sh """
        set +x
        source /etc/profile
        set -x
        cd ${profileBuildRootDir}
        cd ${appConfig.build_dir}
        cd image_build_dir
        tar -cf app.tar ${appConfig.name}
        rm -rf ${appConfig.name}
        cp ${startupScript} ./startup.sh
        cp ${dockerfile} ./Dockerfile
        docker build . --tag ${imageTag}
        docker push ${imageTag}
        docker rmi ${imageTag}
    """
    // TODO 这里有问题，imageName 需要注入到具体的 profile 离去，否则会被覆盖
    appConfig["imageName"] = imageTag
    return appConfig
}

def _buildAppImage(appConfig){
    appConfig.build_profiles.collect { profile -> _buildImageWithProfile(appConfig,profile) }
}

def build(allConfigs){
    def successList = []
    for(def appConfig:allConfigs){
        try{
            _buildAppImage(appConfig)
            successList.add(appConfig)
        }catch(error_msg){
            echo "${_error} - 为APP ${appConfig.name} 构建容器镜像失败"
            continue
        }
    }
    return successList
}