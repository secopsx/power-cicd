#! /bin/bash




# -----------------------------
# 需要构建应用镜像的时候, 或者部署到K8S 的时候设置的变量
# * 必须，· 可选
# APP_GROUP_NAME      * gitlab 代码组
# APP_PROJECT_NAME    * gitlab 工程的名字
# APP_NAME            * 子模块名字
# APP_VERSION         * 子模块版本号
# APP_JAVA_OPTS       · java 的运行时配置，比如: -Xms500m -Xmx1500m
# DEPLOY_ENV          * 部署环境
# K8S_POD_NAME        . K8S POD 的名字
# POD_DELAY_EXIT      . 容器主进程有BUG 无法存活时，开发可能无法进入容器确认问题，因此在java 进程死掉后，
#                            由本shell 脚本sleep POD_DELAY_EXIT 指定的秒数，保活容器，便于开发确认问题
#                            默认 0，即java 进程退出后，容器立即退出

echo    "[DevOps `date -R`] INFO - Start to check required environments"
echo    "[DevOps `date -R`] INFO - All available environments: `env`"
# 参数校验
[ " ${APP_GROUP_NAME}"    == " " ]&&{ echo "[DevOps `date -R`] ERROR - APP_GROUP_NAME is required";  exit 9; }
[ " ${APP_PROJECT_NAME}"  == " " ]&&{ echo "[DevOps `date -R`] ERROR - APP_PROJECT_NAME is required";exit 9; }
[ " ${APP_NAME}"          == " " ]&&{ echo "[DevOps `date -R`] ERROR - APP_NAME is required";        exit 9; }
[ " ${APP_VERSION}"       == " " ]&&{ echo "[DevOps `date -R`] ERROR - APP_VERSION is required";     exit 9; }
[ " ${DEPLOY_ENV}"        == " " ]&&{ echo "[DevOps `date -R`] ERROR - DEPLOY_ENV is required";      exit 9; }
[ " ${K8S_POD_NAME}"      == " " ]&&{ echo "[DevOps `date -R`] ERROR - K8S_POD_NAME is required";    exit 9; }

echo -e "[DevOps `date -R`] INFO - Environments:"
echo -e "[DevOps `date -R`] INFO -   |--APP_GROUP_NAME   :${APP_GROUP_NAME}"
echo -e "[DevOps `date -R`] INFO -   |--APP_PROJECT_NAME :${APP_PROJECT_NAME}"
echo -e "[DevOps `date -R`] INFO -   |--APP_NAME         :${APP_NAME}"
echo -e "[DevOps `date -R`] INFO -   |--APP_VERSION      :${APP_VERSION}"
echo -e "[DevOps `date -R`] INFO -   |--DEPLOY_ENV       :${DEPLOY_ENV}"
echo -e "[DevOps `date -R`] INFO -   |--APP_OPTS         :${APP_OPTS}"
echo -e "[DevOps `date -R`] INFO -   |--POD_DELAY_EXIT   :${POD_DELAY_EXIT}"
echo -e "[DevOps `date -R`] INFO -   \--K8S_POD_NAME     :${K8S_POD_NAME}\n"

source /etc/profile


echo "[DevOps `date -R`] INFO Try to start python application: ${APP_NAME}"
echo "[DevOps `date -R`] INFO ENV: ${DEVOPS_ENV}"
 
cd /data/apps/${APP_NAME}
echo "[DevOps `date -R`] pwd: `pwd`"
echo "[DevOps `date -R`] files: `ls`"
echo "[DevOps `date -R`] CMD: python3 run.py \"${APP_OPTS}\""
#python -m SimpleHTTPServer 8080 2>&1 &
eval "python3 run.py ${APP_OPTS}| tee -a  /data/logs/startup.log"
echo "[DevOps `date -R`] python app process exited" | tee -a  /data/logs/startup.log
sleep ${APP_DELAY_EXIT:-0}