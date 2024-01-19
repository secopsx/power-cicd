package com.cicd.libs


def genextraEnvironmentVariables(envString){
    def extraEnvironmentVariables = [:]
    if(envString){
        for(def keyValue : envString.split(',')){
            assert keyValue.indexOf('=') != -1 : "环境变量格式错误，请参考：key1=value1,key2=value2"
            def key = keyValue.split('=')[0]
            def value = keyValue.split('=')[1]
            extraEnvironmentVariables[key] = value
        }
    }
    return extraEnvironmentVariables
}


def getProfile(codeGroup,codeProject,appName,deployEnv){
    try{
        // 加载runtime profile 的配置文件
        def profileConfigRepo = readJSON file: "${CICD_PROFILES_DIR}/${deployEnv}.json"
        // 提取指定app 的runtime 参数
        def appPrivateConfig = profileConfigRepo.appConfigurations.find{ cfg -> cfg.name == appName && cfg.group == codeGroup && cfg.project == codeProject }
        assert appPrivateConfig : "应用 ${codeGroup}:${appName} 还没有对接到环境：${deployEnv}"
        // 合并该环境下app 的公共参数
        def appConfigFull = profileConfigRepo.defaultAppConfigurations + appPrivateConfig // map 相加，后面的覆盖前面的
        return [
            "envConfigurations": profileConfigRepo.envConfigurations,
            "name": profileConfigRepo.name,
            "appConfigurations":appConfigFull
        ]
    }catch(error_msg){
        error("${_error} - 为应用 ${codeGroup}:${appName} 解析环境 ${deployEnv} 的配置参数失败，error: ${error_msg}")
    }
}



def deployToK8s(profileConfig){
    echo "${_debug} - APP ${profileConfig} 采用helm 部署方式"
    def appConfig = profileConfig.appConfigurations
    def envConfig = profileConfig.envConfigurations
    def extraEnvs = appConfig.environmentVariables + profileConfig.extraEnvironmentVariables
    // 先确认helm release 是否存在（是否已经部署过了），
    // 如果部署过了，则helm 采用 upgrade 方法，否则采用 install 方法
    String inspectShell = """ helm --kubeconfig ${appConfig.k8sKubeconfig} ls \
                              | awk '{print \$1}' \
                              | grep "^${appConfig.name}\$" \
                              | wc -l """
    String isInstalled = sh(returnStdout: true, script: inspectShell).trim()
    String helmMethod = isInstalled=="0" ? "install" : "upgrade"
    echo "${_info} - helm 采用 ${helmMethod} 命令执行操作"
    
    String extraEnvLine = ""
    Integer envIndex = 0
    for(item in extraEnvs){
        extraEnvLine = extraEnvLine + "--set additionalEnv[${envIndex}].name=${item.key} --set additionalEnv[${envIndex}].name=${item.value}"
        envIndex++
    }
    // 根据CI 流水线中Chart 版本规则，构造chartVersion
    String chartVersion = "${appConfig.version.minus('v')}-sha-${appConfig.codeCommitShaShort}-${profileConfig.name}"
    echo "${_info} - chartVersion: ${chartVersion}"
    // 执行部署
    String installShell = """
        set -x
        helm --kubeconfig ${appConfig.k8sKubeconfig} ${helmMethod} \
        ${appConfig.name} \
        ${envConfig.helmRepoAlias}/${appConfig.name} \
        --set APP_VM_OPTS="${appConfig.vmOptions}" \
        --set APP_OPTS="${appConfig.appOptions}" \
        --set CPU_LIMIT=${appConfig.cpuLimit} \
        --set CPU_REQUEST=${appConfig.cpuRequest} \
        --set RAM_LIMIT=${appConfig.ramLimit} \
        --set RAM_REQUEST=${appConfig.ramRequest} \
        --set UPDATE_RECREATE=${appConfig.updateRecreate} \
        --set REPLICAS=${appConfig.appReplicas} \
        ${extraEnvLine} \
        --devel --version ${chartVersion}
    """
    echo "${_info} - 将执行如下shell 进行部署：\n${installShell}"
    // sh """ ${installShell} """
}



def  deploy(profileConfig){
    switch(profileConfig.appConfigurations.deployType) {
        case "HELM":
            deployToK8s(profileConfig);
            break;
        default:
            error("${_error} - 暂不支持的部署方式，请对接运维");
            break;
    }
}