package com.cicd.libs


def _buildChartWithProfile(appConfig,profile){
    def chartTemplate = "${CI_CHART_TEMPLATES_DIR}/${appConfig.chart_template}"
    appConfig['chartVersion'] = "${appConfig.version.minus('v')}-sha-${CODE_COMMIT_SHA_SHORT}-${profile.name}"
    def chartResultDir = new File("${appConfig.appAbsoluteBuildDir}/chart_build_dir/${appConfig.name}")
    chartResultDir.mkdirs()

    echo "${_debug} - 开始为APP ${appConfig.name} 渲染 Chart: \n"+
         "    |--- Profile Name  : ${profile.name} \n"+
         "    |--- Profile Detail: ${profile} \n"+
         "    |--- ImageName     : ${appConfig.imageName} \n"+
         "    |--- ChartVersion  : ${appConfig.chartVersion} \n"+
         "    |--- ChartResultDir: ${chartResultDir} \n"+
         "    |--- chart_temlate : ${chartTemplate}"

    sh """
        export DEPLOY_ENV=${profile.name}
        export APP_HEALTH_CHECK_PATH=${appConfig.healthCheckPath}
        export APP_IMAGE=${appConfig.imageName}
        export IMAGE_PULL_SECRET=${profile.envConfigurations.imagePullSecret}
        export APP_GROUP_NAME=${appConfig.group}
        export APP_PROJECT_NAME=${appConfig.project}
        export APP_NAME=${appConfig.name}
        export APP_VERSION=${appConfig.version}
        export APP_CHART_VERSION=${appConfig.chartVersion}
        export APP_BUILD_TIME=${appConfig.ciInitTime}
        export APP_COMMIT_MESSAGE=${appConfig.commitMessage}
        export APP_CODE_BRANCH=${appConfig.codeBranch}
        export APP_COMMIT_SHA=${appConfig.codeCommitSha}
        export APP_COMMIT_SHA_SHORT=${appConfig.codeCommitShaShort}
        cd ${chartResultDir}/..
        pwd
        sh ${CICD_TOOL_SCRIPTS_DIR}/render_chart.sh ${chartTemplate} ${appConfig.name}
        helm package ./${appConfig.name}
        _package_name=`ls -1 |grep -E "${appConfig.name}-[0-9]+" |grep tgz\$`
        helm cm-push \${_package_name} ${profile.envConfigurations.helmRepoAlias}
    """
    return appConfig
}

def _buildAppChart(appConfig){
    appConfig = appConfig.build_profiles.collect { profile -> _buildChartWithProfile(appConfig,profile) }
    return appConfig
}

def build(allConfigs){
    def successList = []
    for(def appConfig:allConfigs){
        try{
            successList.add(_buildAppChart(appConfig))
        }catch(error_msg){
            echo "${_error} - 为APP ${appConfig.name} 构建Chart失败:${error_msg}"
            continue
        }
    }
    return successList
}