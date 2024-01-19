#!/usr/bin/env python3
#encoding:utf-8
#dep:
# - python-gitlab

# 用于jenkins pipline，从master 分支创建release 分支


import gitlab
# pip3 install python-gitlab


if __name__ == "__main__":
    import argparse
    import os
    import sys
    from urllib.parse import urlparse

    parser = argparse.ArgumentParser()
    parser.add_argument('--project',   '-p', dest='project',   action='store', required=True,  help='指定项目名字')
    parser.add_argument('--merge',     '-m', dest='merge_id',  action='store', required=True,  help='指定merge 的sha')
    parser.add_argument('--branch',    '-b', dest='new_branch',action='store', required=True,  help='待创建的新分支的名字')
    parser.add_argument('--log-file',  '-l', dest='log_file',  action='store', default="stdout",required=False,  help='日志文件，默认：stdout')
    parser.add_argument('--log-level', '-v', dest='log_level', action='store', default="debug", required=False,  help='日志等级，默认：debug',
                                             choices=["error","debug","info","warn"])
    args = parser.parse_args()
    
    gitlab_api    = os.getenv("CI_GITLAB_URL")
    gitlab_token  = os.getenv("CI_GITLAB_TOKEN")
    assert gitlab_api, "[\033[1;31m  ERROR\033[0m] - 无法从环境变量 CI_GITLAB_URL 中获取gitlab api 地址"
    assert gitlab_token, "[\033[1;31m  ERROR\033[0m] - 无法从环境变量 CI_GITLAB_TOKEN 中获取gitlab api token"

    if 'https://' in args.project:
        parsed_url = urlparse(args.project)
        args.project = parsed_url.path.split('.')[0].strip('/')
    else:
        pass

    try:
        gitlab_cli = gitlab.Gitlab(gitlab_api,private_token=gitlab_token,keep_base_url=True)
        project_obj = gitlab_cli.projects.get(args.project)
    except Exception as e:
        print(f"[\033[1;31m  ERROR\033[0m] - 获取gitlab 工程对象失败，msg:{str(e)}")
        sys.exit(1)
    
    # 创建新的release分支
    if args.new_branch not in map(lambda x:x.name,project_obj.branches.list()):
        nb = project_obj.branches.create({"branch":args.new_branch,"ref":args.merge_id})
    else:
        print(f"[\033[1;34m  DEBUG\033[0m] - 分支{args.new_branch} 已经存在，跳过")
    # 将新的release 分支加入到保护分支清单
    if args.new_branch not in map(lambda x:x.name,project_obj.protectedbranches.list()):
        pb = project_obj.protectedbranches.create({
               "name": args.new_branch,
               "merge_access_level": gitlab.const.MAINTAINER_ACCESS,
               "push_access_level": gitlab.const.NO_ACCESS
             })
    else:
        print(f"[\033[1;34m  DEBUG\033[0m] - 分支{args.new_branch} 已经是受保护分支，跳过")
    
