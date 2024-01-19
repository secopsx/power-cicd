#!/usr/bin/env python3
#encoding:utf-8
#dep:
# - python-gitlab


import gitlab
# pip3 install python-gitlab

class GroupProjects(object):
     def __init__(self, group, gitcli, logger=None,keywd=None):

          self.logger = logger
          
          if isinstance(gitcli, gitlab.Gitlab):
              self.gitcli = gitcli
          elif isinstance(gitcli,tuple):
              self.gitcli = gitlab.Gitlab(gitcli[0], private_token=gitcli[1])
          else:
               _error = "gitcli should be a instance of gitlab.Gitlab or tuple of gitlab address and token"
               if self.logger: self.logger.error(_error)
               assert False, _error
          self.group      = self.gitcli.groups.get(group)
          self.sub_groups = self.group.subgroups.list(all=True)
          _projects       = self.group.projects.list(all=True)
          if keywd:
              _projects   = list(filter(lambda x:x.name in keywd, _projects))
          self.projects   = []
          _pids = list(map(lambda x:x.id, _projects))
          for pid in _pids:
               self.projects.append( self.gitcli.projects.get(pid) )


          self.projects_sg = []
          for sg in self.sub_groups:
               _sub_group      =  self.gitcli.groups.get(sg.id)
               _projects_in_sg =  _sub_group.projects.list(all=True)
               if keywd:
                  _projects_in_sg = list(filter(lambda x:x.name in keywd, _projects_in_sg))
               _projects_ids = list(map(lambda x:x.id, _projects_in_sg))
               for pid in _projects_ids:
                    self.projects_sg.append( self.gitcli.projects.get(pid) )

          self.all_projects = self.projects + self.projects_sg

     def projectIDs(self, all=True):
          ''' retrun the project IDs '''
          _projects_ids = list(map(lambda x:x.id, self.all_projects if all else self.projects))
          return _projects_ids

     def projectNames(self, all=True):
          ''' retrun the project Names '''
          _project_names = list(map(lambda x:x.name, self.all_projects if all else self.projects))
          return _project_names
     
     def allProjects(self, all=True):
          ''' retrun  [(projectName,projectID),] '''
          _projects = list(map(lambda x:(x.name,x.id), self.all_projects if all else self.projects))
          return _projects
     def allProjectObjects(self, all=True):
          ''' return gitlab projects '''
          _projects = self.projects if all else self.projects_sg
          return _projects


if __name__ == "__main__":
    import argparse
    import os
    import sys

    parser = argparse.ArgumentParser()
    parser.add_argument('--project',   '-p', dest='project',  action='store', required=True,  help='指定项目名字')
    parser.add_argument('--group',     '-g', dest='group',    action='store', required=True,  help='指定组')
    parser.add_argument('--branch',    '-b', dest='branch',   action='store', required=True,  help='指定分支')
    args = parser.parse_args()
    
    gitlab_api = os.getenv("CI_GITLAB_URL")
    assert gitlab_api, "无法从环境变量 CI_GITLAB_URL 中获取gitlab api 地址"
    gitlab_token = os.getenv("CI_GITLAB_TOKEN")
    assert gitlab_token, "无法从环境变量 CI_GITLAB_TOKEN 中获取gitlab api token"
    ding_contacts = os.getenv("DING_CONTACTS")
    assert ding_contacts, "无法从环境变量 DING_CONTACTS 中获取通讯录"

    #gitlab_cli = gitlab.Gitlab(gitlab_api, private_token=gitlab_token,keep_base_url=True)
    #有的版本不接受 keep_base_url
    gitlab_cli = gitlab.Gitlab(gitlab_api, private_token=gitlab_token)
    group_object = GroupProjects(args.group, gitlab_cli,keywd=[args.project])
    all_projects = group_object.allProjectObjects()
    project_obj = list(filter(lambda x: x.name == args.project, all_projects))
    assert project_obj, "指定的项目不存在"
    project_obj = project_obj[0]
    branches:list = project_obj.branches.list(get_all=True)
    target_branche = list(filter(lambda x:x.name == args.branch,branches))
    assert target_branche,"该项目下没有制定的分支"
    target_branche = target_branche[0]
    print(target_branche.commit["id"])