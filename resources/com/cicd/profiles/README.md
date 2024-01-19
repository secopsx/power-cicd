```json
{
    "envConfigurations": {
        "harborAddress": "harbor.local.xx.com",
        "harborProject": "project",
        "helmRepoAlias": "local_project",
        "imagePullSecret": "default-pull-secret",
        "kuboardAddress": "http://192.168.10.103:24080"
    },
    "defaultAppConfigurations": {
        "environmentVariables": {
            "APOLLO_ADDRESS": "http://apollo-configservice-0.apollo-configservice.xx.svc.cluster.local:8080"
        },
        "k8sNamespace": "prod",
        "k8sKubeconfig": "jiujiang-test",
        "appReplicas": 2,
        "cpuLimit": "200m",
        "cpuRequest": "100m",
        "ramLimit": "200Mi",
        "ramRequest": "100Mi",
        "autoDeploy": false,
        "runtime_options": "",
        "app_options": ""
    },
    "appConfigurations":[
        {
            "group": "iot",
            "project": "x-iothub-devicesimulator",
            "name": "x-iothub-devicesimulator",
            "autoDeploy": true,
            "environmentVariables": {
                "APOLLO_IP": "http://apollo-configservice-0.apollo-configservice.xx.svc.cluster.local:8080",
            }
        }
    ]
}
```