//  appConfigs 的元素的 build_profiles 字段是List[String] 类型，
//  这里根据具体的profile，从CMDB 中获取该profile 的具体配置，比如 harbor 地址，apollo 地址等
//  再将 build_profiles 字段替换为 List[profile:map] 类型
def _get_profile_info(profile,app){
    def thisFilePath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath()
    def profileFilePath = "${thisFilePath.split('/vars/')[0]}/resources/com/cicd/profiles/${profile}.json"
    assert fileExists(profileFilePath) : "profile ${profile} 对应的配置文件 ${profileFilePath} 不存在，请联系运维补充"
    // 如果转换json 失败，就让它抛异常退出
    def profileObject = readJSON file: profileFilePath
    // profileObject 中包含了这个 profile 下的公共参数，以及所有APP 的运行时参数
    // 因此要保留公共参数和指定APP 的运行时参数，提出其他APPs 的运行时参数
    // [
    //    "envConfigurations":[],
    //    "appConfigurations":[]
    // ]
    def appConfiguration = profileObject.appConfigurations.find{ cfg -> cfg.name == app.name && cfg.group == app.group && cfg.project == app.project }
    def appDefaultConfiguration = profileObject.defaultAppConfigurations
    appDefaultConfiguration.putAll(appConfiguration)
    def appProfile = [
        "envConfigurations": profileObject.envConfigurations,
        "appConfigurations": appDefaultConfiguration,
        "name": profileObject.name
    ]
    return appProfile
}

def _fill_profiles(appConfig){
    def profiles = appConfig.build_profiles.collect { profileName -> _get_profile_info(profileName,appConfig) }
    appConfig.build_profiles = profiles
    return appConfig
}

def fillProfile(appConfigs){
    def resultList = appConfigs.collect { value -> _fill_profiles(value) }
    echo "${_debug} - 填充profile 信息后：${resultList}"
    return resultList
}