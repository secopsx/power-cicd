# 前言
> “吾尝终日而思矣，不如须臾之所学也；吾尝跂而望矣，不如登高之博见也。”<br>——荀子《劝学》，距今2000多年

接触和使用jenkins 流水线3年，遇到过许多的困难和挑战。现在回想起来，发现大多问题都是对知识的掌握不到位，古之人诚不我欺。本项目代表了我的一些经验、理念以及成果，希望能帮到有需要的人。

# 观念
## **一、CICD 的基石：流程规范**
CICD的基础在于 **`代码版本管理`**，**`代码分支管理`**，**`代码配置管理`**，**`代码质量管理`**，**`应用迭代流程`**。而CICD 的工具，仅仅是这些流程规范的代码化实现而已。如果研发、测试、运维没有就这些流程规范达成共识，建设CICD 的意义就不大，因为自动化的前提就是标准化。

如果达成了共识，CICD 系统的设计则不必拘泥于实现方式，Gitlab CI，Jenkins，ArgoCD，甚至自行开发工具都可以，但是有一些基本的设计准则： **`一致性`**、**`幂等性`**、**`可配置`**、**`可感知`**、**`可管控`**、**`需脱敏`**。


## **二、CICD 流水线的设计准则**
### 2.1 一致性

应用从代码提交到生产环境上线要经理很多环节的考研，比如 **`静态审查`** -> **`单元测试`** -> **`接口测试`** -> **`集成测试`** -> **`UAT`** -> **`preDeploy`** -> **`prodDeploy`** 等。很明显，如果后续环节部署的应用和前序环节部署的应用存在差异，那这套复杂的流程就失去了意义。

#### 2.1.1 哪些业务场景需要保证严格的一致性？

我认为这应该是一种很基本的理念，但人力劳动总会关乎成本。如果生产环境出点故障不会造成什么经济损失，能尽快修复即可，那么可以放弃一致性，转而聚焦于效率。但是对于金融，工控，智能汽车控制系统等强监管的领域，或者生产环境故障会造成经济损失的行业（比如电商，损失 = `平均订单并发` * `故障时间` * `平均客单价`）,强调一致性是非常有必要的。

#### 2.1.2 一致性问题的根源是什么？

**（1）空间维度**

同一套代码，运行环境不同，相同的业务流程具有不同的处理逻辑。这些不同的处理逻辑可能源于测试环境无法具备和生产环境一样的环境依赖；甚至可能来自于人为的、主观的、恶意的植入，比如：`npm run build:prod` 引入恶意代码，但是测试环境里部署的是用命令`npm run build:fat`构建的包，那么测试环境是无法发现恶意代码的。

给构建命令传入不同的参数，将得到不同的代码，我们称这样的参数为： **`build_profile`**。 `build_profile`主要用于为代码指定不同的环境依赖，但是解决环境依赖的手段不止这个，我个人非常反对这个，因为容易被滥用。替代方案可以是在运行时传递不同的参数，比如测试环境为支付接口配置沙箱地址。也可以是为测试环境设置独立的DNS 解析。**总而言之，就是不要让给研发任何机会设置“误差”，这是运维的职责**。

**（2）时间维度**

同一套代码，构建时间不同，编译器的不确定性和依赖对象版本的不确定性导致产出不同的代码。比如A 应用依赖 `B>=1.2.0`，如果B 没有按照约定去迭代，在更新的版本中引入了**BUG**、**漏洞**甚至**不能向前兼容的改动**，即使我们用相同的构建命令去构建同一套代码，也将得到不同的结果。

有些公司的CICD 流程不规范，比如部署测试环境的时候临时拉取测试分支进行构建和部署，部署生产环境时则拉取生产分支进行构建和部署，这期间的时间间隔可能以月为单位，足以掉进时间维度的一致性深渊。

#### 2.1.3 一致性问题的解决方案
很明显，**禁止`build_profile`**，保证CICD 各个环境所使用的代码是一致的；并且**一次构建到处运行**（代码离开开发分支，进入稳定分支后只构建一次，后续各个环境都使用相同的工件进行部署），避免时间维度引入的不确定性。


### 2.2 幂等性

幂等性强调的是操作的目标效果，它要求操作对目标产生的影响是确定的、唯一的。实现幂等性，能让运维操作高效、可靠，降低触发不确定性后果的几率。

比如我们要启动一个应用进程，如果该应用进程已经存在，就应该什么也不做，而不是额外再启动一个新进程，也不是将旧进程杀死再启动新进程。再比如我们要传输一个文件，如果该文件已经存在于目标位置，且MD5 值也一样，就不用产生实际传输，避免带宽占用和IO 消耗。

>一个典型的替代型的发布流程一般是：传输安装包到指定位置；将实例从负载均衡器中剔除；等待当前连接和任务结束；停止当前版本进程；替换安装包；启动新版本；等待健康检查通过；接入负载均衡器。如果我们人为误操作，或者系统设计有BUG，导致版本发布重复执行，那么整个都会执行一遍。如果业务代码和系统架构都比稳定和合理，那么这个流程走下来理论上也不会影响生产环境的使用，但是业务无辜占用CICD 系统的资源。如果业务代码和系统架构不是那么OK，这个操作将影响系统整体的稳定（比如一些应用启动时需要读取大量的数据用于构建本地缓存，对数据库产生压力）。

>当前K8S、helm 等系统和工具都是严格遵守幂等原则的。


### 2.3 可配置
前面提到 CICD 系统是一系列流程规范的代码实现，所谓规则，无外乎是“A事务是否可以执行B操作”、“A事件发生时该执行什么操作”这类的判断。这些规则关联的主体以及规则的定义都需要可配置，而不是在代码中用 if else 去穷举。换言之，CICD 系统的开发，应该更贴近于**声明式编程**，而非命令式编程。这样的系统才能称之为流水线，才能具备良好的适应性，避免新增应用，新增环境时都去改动大量的代码来适配。

比如在分支管理中，当开发人员提起合并请求（MR，MergeRequest）时，流水线如何判断是否允许？是否要执行静态审查？
对于这个问题，我们可以设置一个规则列表，CICD 代码只需要从MR 事件中提取变量，并逐一匹配规则列表。下面是一个可运行的示例，我们可以在 `BRANCH_NAME_RULES_WHEN_MR` 变量中添加任意规则。
```bash
#!/bin/bash
# 
# 注意，并用bash 执行，有些环境下 sh 指向 dash，或者其他，可能遇到语法错误
# 注意，这里为了简化规则引擎的实现，使用了eval 执行外部指令，这是不安全的，仅用于演示可配置性的原理
# 

# 这些变量，Gitlab 会自动注入到流水线runtime，这里赋值是便于读者运行验证
# 如果使用Gitlab 事件触发 Jenkins pipeline, 可从Jenkins pipeline 的 env 中获取 gitlabTargetBranch 和 gitlabSourceBranch
CI_MERGE_REQUEST_SOURCE_BRANCH_NAME="feature-wonderful-function"
CI_MERGE_REQUEST_TARGET_BRANCH_NAME="develop"
BRANCH_NAME_RULES_WHEN_MR=(
'"${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME}" == develop   && "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" == master'
'"${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME}" == hotfix*   && "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" == *Release*'
'"${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME}" == *Release* && "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" == master'
'"${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME}" == feature*  && "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" == develop'
'"${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME}" == hotfix*   && "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" == develop'
'"${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME}" == *-develop && "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" == *Project*'
'"${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME}" == develop   && "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" == *Project*'
'"${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME}" == *Release* && "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" == *Project*'
'"${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME}" == hotfix*   && "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" == *Project*'
)

PASSED_RULE="none"
for rule in "${BRANCH_NAME_RULES_WHEN_MR[@]}";do
    # 规则从外部，比如配置文件，CMDB 中获取，
    # 对于流水线来说，这属于外部数据，这里直接用eval 执行是存在安全风险的，仅仅是为了简化规则引擎的实现，
    # 便于展示核心逻辑
    eval "[[ ${rule} ]]&&PASSED_RULE=\"${rule}\""
    [ $? == 0 ]&&{ break; }
done

if [ "${PASSED_RULE}" != "none" ];then
    echo "MR 事件中的源分支和目标分支符合规则： ${PASSED_RULE}，可以放行";
else
    echo "MR 事件中的源分支和目标分支没有匹配任何允许的规则，不予放行";
    exit 1;
fi
```



### 2.4 可感知

CICD 系统关乎软件迭代的效率，因此流水线要将运行状态实时汇报给干系人，以便在出现错误时及时介入。本项目主要以钉钉为通知渠道，流水线运行的任何状态和结果都可以通过钉钉@ 到具体的人。后续可以接入企业微信，邮件等方式。

### 2.5 可管控
CICD 系统终究不可能做到一劳永逸，其本身也需要不断迭代，因此流水线代码应该是集中管理的。我们不认同将cicd 流水线代码放置到每个git 工程里去，因为那样做有以下弊端：
- （1）代码分散，不利于管理。如果公司有1000个代码工程，光是设置和变更流水线就难以实现；
- （2）权限酚酸，不利于管控。任何有代码编辑权限的人都可以修改流水线。

不管是采用Jenkins Pipeline ，还是gitlab-ci，都应该集中设置和管控流水线代码。

### 2.6 需脱敏
流水线的输出信息中容易包含一些敏感信息，比如流水线三方系统交互时容易暴露账号密码，因此要特别关注这一点。