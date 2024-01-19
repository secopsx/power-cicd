// 关于 profile
// =========================
// profile 分为2类，一类是作用于构建过程的，另一类是作用于运行时的
// （1）构建时（build profile）
//     用于指示构建工具按照指定的配置去构建APP，最典型的就是 前端代码: npm run build:${profile}
// 
// （2）运行时（runtime profile)
//     比如使用apollo 作为配置管理器时，java 代码需要获取指定 profile 下的配置
// 
// 最佳实践
// =========================
// 我们希望稳定代码是一次构建，任意环境运行，以保持一致性，这里有2个概念：
// （1）稳定代码
//     代码在开发环境或者联调环境里评估通过，进入正式的测试阶段后，该代码是稳定代码（即使后续测试过程中
//     会出现修修补补，但是功能特定是稳定的了）。稳定代码的迭代，只允许修改BUG。体现在版本号上就是只能
//     修改 vX.Y.Z 中的Z。稳定代码可以通过tag 来标识；也可以通过分支来标识，我们一般人为release 分支
//     和master 分支是稳定代码；
// 
// （2）一致性 之 依赖风险
//     代码项目相互依赖，编译器自身的不确定性，都将导致同一份代码在不同时间点构建出来的结果有所差异，
//     这些差异很可能未经测试就潜伏到了生产环境, 比如：某个java 代码依赖模块 A>=1.2.0，但是模块A 自行
//     迭代，很可能引入BUG，甚至不能向前兼容。
//     我们不是不允许 “>=”这样的依赖方式，而是要求每次构建时引入的外部代码都要经过测试。如果代码在测试
//     环境测试通过，但是在发布到生产环境前重新构建，就会存在这样的【依赖风险】。
// 
// （3）一致性 之 测试覆盖风险
//     另外一个方面，目前大前端领域很繁荣，但是前端代码主流的构建方式存在更大的风险，比如：
//     - 测试环境：npm run build:test ----> 测试环境测试
//     - 生产环境：npm run build:prod ----> 部署到生产环境
//     这两个构建命令得到的部署包可以包含不同的代码(甚至后门)，这将导致生产环境存在未经测试覆盖的风险，
// 
//   基于上述原则，我们【不】希望设置任何 build profile，因为不同 build profile 可能产出完全不同
//   的代码，导致测试疏漏，引发生产故障。但是现实总是骨感的，由于种种原因 build profile 还是会客观存在
//   因此本系统保留这个概念作为紧急的“逃生口”，但是切勿滥用。
// 


def getAllAPPConfig(){ 
return [
    // 字段解释
    // code_type : 指示代码类型，可选值： frontend|golang|java|python等，流水线可能根据代码类型做不同处理
    // profiles: 
    //       ：定义不同分支所使用的 profile ，比如：
    //       : - develop 分支对应使用的 profile 为 dev
    //       : - 符合 .*%Release-.* 的分支，其 profile 则为 test 和 pro，标识并行构建测试和生产环境
    //       :
    //       : ***Notice****
    //       : (1) 这个 profile 将被注入到环境变量 APP_BUILD_PROFILE，build_command 字段的值可以引用，
    //       :     比如 build_command : npm run build:\${APP_BUILD_PROFILE}
    //       : 
    //       : (2) 一些代码的业务逻辑完全取决于运行时给定的参数，因此可以做到一次构建，任意环境运行
    //       :     我们鼓励这种模式，因为他能做好很好的一致性。
    //       :     此时，可以为【稳定分支】设置通用的 profile 值： release
    //       :
    //       : (3) profile 的值会出现在工件（二进制，jar 包，package 包，容器镜像，HelmChart 包）的命名中
    //       :     比如二进制文件命名：${name}-vX.Y.Z-sha-XXXXXX-${APP_BUILD_PROFILE}
    [
        "code_type"          : "frontend",
        "name"               : "xx-iot",
        "project"            : "xx-iot",
        "group"              : "FrontEndGroup",
        "build_dir"          : ".", 
        "build_command"      : "echo npm run build:\${APP_BUILD_PROFILE} >> build_res.jar",
        "chart_template"     : "go-template",
        "dockerfile"         : "go-template",
        "version_file"       : "version.txt",
        "build_results"      : "./build_res",
        "startup_script"     : "go-startup.sh",
        "branch_profiles"    : [
            "%Release-"      : ["test","pro"],
        ]
    ],
    [
        "code_type"          : "java",
        "name"               : "cicd-test-project",
        "project"            : "cicd-test-project",
        "group"              : "ops",
        "build_dir"          : ".", 
        "build_command"      : "echo npm run build:\${APP_BUILD_PROFILE} >> build_res.jar",
        "chart_template"     : "spring-template",
        "dockerfile"         : "spring-template",
        "version_file"       : "version.txt",
        "build_results"      : "./build_res.jar",
        "startup_script"     : "jar-startup.sh"
    ],
    [
        "code_type"          : "java",
        "name"               : "another_sub_app",
        "project"            : "cicd-test-project",
        "group"              : "ops",
        "build_dir"          : ".", 
        "build_command"      : "echo \${_error} - 模拟构建失败&&exit 1",
        "chart_template"     : "spring-template",
        "dockerfile"         : "spring-template",
        "version_file"       : "version.txt",
        "build_results"      : "./build_res.jar",
        "startup_script"     : "jar-startup.sh"
    ]
] 
}

def getDefaultBranchProfile(codeBranch){
    def defaultProfilesOfBranch = [
        "%Release-": ["release",],
        "develop": ["dev",],
    ]
    if(codeBranch.indexOf('%Project-') != -1 || codeBranch.indexOf('%armProject-') != -1){
        // 项目分支的格式：${APPName}%${ProjectName}-${ProjectEnv}%Project-${APPVersion}
        // 比如： public-gateway%someproject-prod%Project-1.2.x
        // 我们提取 ${ProjectName}-${ProjectEnv} 这个部分作为 build profile
        String profile = codeBranch.split('%')[1]
        defaultProfilesOfBranch[profile] = [profile,]
    }
    return defaultProfilesOfBranch
}


def fillProfiles(appConfig,codeBranch){
    def defaultProfile = getDefaultBranchProfile(codeBranch)
    if(!appConfig.containsKey('branch_profiles')){
        appConfig['branch_profiles'] = defaultProfile
    }else{
        appConfig.branch_profiles.putAll(defaultProfile)
    }
    // 根据 codeBranch，从 appConfig.branch_profiles 找到本次需要执行的 profiles
    // 并作为新属性 build_profiles 增加到 appConfig
    def matchedProfiles = appConfig.branch_profiles.findAll {
        branchName,branchProfiles -> codeBranch.indexOf(branchName) != -1
    }
    if(matchedProfiles){
        appConfig['build_profiles'] = matchedProfiles.values()[0]
    }else{
        println "${env._warn} - 分支${codeBranch} 没有匹配到任何 profile，检索的profile 有：${appConfig.branch_profiles}"
        appConfig['build_profiles'] = []
    }
    return appConfig
}

def get(codeGroup,codeProject,codeBranch,appOfProject){
    String _registered = "false"
    String _error_msg  = ""
    List _PipeLinePayload = []
    List allAPPConfig = getAllAPPConfig()

    if(appOfProject == "ALL"){
        _PipeLinePayload = allAPPConfig.findAll { app -> app.group == codeGroup && app.project == codeProject }
    }else{
        _PipeLinePayload = allAPPConfig.findAll{ app -> app.group == codeGroup && app.project == codeProject && app.name == appOfProject }
    }
    // 植入默认的profiles
    def PipeLinePayload = _PipeLinePayload.collect { app -> fillProfiles(app,codeBranch) }
    echo "${_debug} - 将构建如下应用：${PipeLinePayload}"
    return PipeLinePayload
}