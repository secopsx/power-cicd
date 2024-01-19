#!/bin/bash

CODE_PROJECT_URL_OAUTH=$1
CODE_BUILD_DIR=$2
CODE_CACHE_DIR=$3
CHECKOUT_TRY_COUNT=$4

echo "pwd: "`pwd` | tee -a ${CICD_PIPELINE_LOG}
 
# 如果拉取2次都失败了，那就把本地缓存删除一下再继续尝试
[ ${CHECKOUT_TRY_COUNT} == "2" ]&&{ rm -rf ${CODE_CACHE_DIR}; }
 
if [ ! -d ${CODE_CACHE_DIR} ] # 不存在，则使用 git clone
then
    echo "${_debug} - try to git clone app" | tee -a ${CICD_PIPELINE_LOG}
    echo "${_debug} - oauth url: ${CODE_PROJECT_URL_OAUTH}" >> ${CICD_PIPELINE_LOG}
    git clone -b ${CODE_BRANCH} ${CODE_PROJECT_URL_OAUTH} ${CODE_CACHE_DIR} >> ${CICD_PIPELINE_LOG} 2>&1
    echo "${_debug} - git clone success, will checkout commit: ${CODE_CHECKOUT_POINT}"
    cd ${CODE_CACHE_DIR}
    git checkout ${CODE_CHECKOUT_POINT} | tee -a ${CICD_PIPELINE_LOG}
else
    # 存在， 则使用 git pull
    echo "${_debug} - try to git pull" | tee -a ${CICD_PIPELINE_LOG}
    cd ${CODE_CACHE_DIR}
    git checkout ${CODE_BRANCH} >> ${CICD_PIPELINE_LOG} 2>&1
    git pull >> ${CICD_PIPELINE_LOG} 2>&1
    echo "${_debug} - git pull success, will checkout commit: ${CODE_CHECKOUT_POINT}" | tee -a ${CICD_PIPELINE_LOG}
    git checkout ${CODE_CHECKOUT_POINT}
    echo "${_debug} - git pull complete." | tee -a ${CICD_PIPELINE_LOG}
fi