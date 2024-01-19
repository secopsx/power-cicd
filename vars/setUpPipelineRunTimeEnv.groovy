def call(){
    startDate = new Date()
    env.CICD_INIT_DATE = startDate.format("yyyyMMddhhmmss")
    env.CICD_INIT_TIME = startDate.getTime()
    echo "${_info} - 当前时间: ${CICD_INIT_DATE}，时间戳： ${CICD_INIT_TIME}"
    
    // 流水线临时目录和日志，用于输出日志，存放临时文件等
    env.CICD_PIPELINE_TMP = "/tmp/CICD/CI/${CICD_INIT_TIME}"
    env.CICD_PIPELINE_LOG = "${CICD_PIPELINE_TMP}/pipeline.log"
    sh "mkdir -p ${CICD_PIPELINE_TMP}" // 不能用 new File 对象，会被沙箱拦截
    echo "${_info} - 流水线临时目录： ${CICD_PIPELINE_TMP}, 日志文件： ${CICD_PIPELINE_LOG}"
    
    // 流水线代码运行时的相关路径
    def _thisFilePath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath()
    env.CICD_LIB_ROOT_DIR = _thisFilePath.split('/vars/')[0]
    env.CICD_RESOURCES_DIR = "${CICD_LIB_ROOT_DIR}/resources/com/cicd"
    env.CICD_TOOL_SCRIPTS_DIR = "${CICD_RESOURCES_DIR}/tool_scripts"
    env.CI_DOCKERFILES_DIR = "${CICD_RESOURCES_DIR}/dockerfiles"
    env.CICD_PROFILES_DIR = "${CICD_RESOURCES_DIR}/profiles"
    env.CI_CHART_TEMPLATES_DIR = "${CICD_RESOURCES_DIR}/chart_templates"
    env.CI_APP_STARTUP_SCRIPTS_DIR = "${CICD_RESOURCES_DIR}/startup_scripts"

    // Gitlab 
    env.CI_GITLAB_TOKEN = "glpat-xUXcnPE87sT3_d3KTCWD"
    env.CI_GITLAB_URL = "https://src.local.xx.com" //最后不要带斜杠 /

    // 检出代码的缓存目录
    env.CI_CODE_CACHE_BASE = "/data/ci_code_cache_dir"
    
    // 钉钉机器人
    env.CICD_DING_TOKEN  = "2ed739ca87dcd4a7e7ddc80817a5f34d6d41c6bcb8b27e081e302b85f1663ee3"
    env.CICD_DING_API    = "https://oapi.dingtalk.com/robot/send"
    env.CICD_DING_KEY_WD = "CI-Notice"
}