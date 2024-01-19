#!/usr/bin/env python3
#encoding:utf-8
#dep:
# - python-gitlab==3.13.0
# - urllib3==1.26.6

# 用于jenkins pipline， 获取当前Merge 的代码贡献者（commiter）
#
# xx-dev 分支 ------[commit 1] --->[commit 2]---\
#                                                \
# dev    分支 ---------------------------------[MergeEvent]
#
# Merge 一般由业务线负责人操作，但是他很可能不是代码的实际贡献者，
# 那么钉钉通知 CICD 事件就最好能通知到 实际代码的提交者 xx


import gitlab
# pip3 install python-gitlab
import argparse
import os
import sys
import json
from urllib.parse import urlparse

parser = argparse.ArgumentParser()
parser.add_argument('--project',   '-p', dest='project',  action='store', required=True,  help='指定项目名字')
parser.add_argument('--commit-sha','-c', dest='commitsha',action='store', required=True,  help='指定merge 的sha')
parser.add_argument('--log-file',  '-l', dest='log_file', action='store', default="stdout",required=False,  help='日志文件，默认：stdout')
parser.add_argument('--log-level', '-v', dest='log_level',action='store', default="debug", required=False,  help='日志等级，默认：debug',
                                         choices=["error","debug","info","warn"])
args = parser.parse_args()
    
gitlab_api = os.getenv("CI_GITLAB_URL")
gitlab_token = os.getenv("CI_GITLAB_TOKEN")

assert gitlab_api, "无法从环境变量 CI_GITLAB_URL 中获取gitlab api 地址"
assert gitlab_token, "无法从环境变量 CI_GITLAB_TOKEN 中获取gitlab api token"

if 'https://' in args.project:
    parsed_url = urlparse(args.project)
    args.project = parsed_url.path.split('.')[0].strip('/')
else:
    pass

try:
     gitlab_cli  = gitlab.Gitlab(gitlab_api, private_token=gitlab_token,keep_base_url=True)
     project_obj = gitlab_cli.projects.get(args.project)
except Exception as e:
     print(f"获取gitlab 工程对象失败，msg:{str(e)}")
     sys.exit(1)

merge_cmt = project_obj.commits.get(args.commitsha)
related_cmts = list(map(lambda x:project_obj.commits.get(x), merge_cmt.parent_ids))
related_cmts.append(merge_cmt)
committer_names = list(map(lambda x:x.committer_name, related_cmts))
all_users_objs = gitlab_cli.users.list(get_all=True)
cmt_users = list(filter(lambda x:x.name in committer_names, all_users_objs))
users_dict = [ json.loads(x.to_json()) for x in cmt_users ]
print(json.dumps(users_dict))
