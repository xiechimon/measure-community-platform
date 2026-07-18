pipeline {
    agent any

    parameters {
        // 🟢 使用 Git Parameter 插件实现下拉选择
        // name: 变量名，在后面脚本中通过 params.BRANCH_NAME 引用
        // type: PT_BRANCH 代表只显示分支；如果你想选标签，可以改为 PT_TAG 或 PT_BRANCH_TAG
        // defaultValue: 默认选中的值
        // branchFilter: 过滤分支，'.*' 代表匹配所有远程分支
        gitParameter(
            name: 'BRANCH_NAME',
            type: 'PT_BRANCH',
            defaultValue: 'main',
            description: '请从下拉列表中选择要发布的分支',
            branchFilter: 'origin/(.*)',
            sortMode: 'ASCENDING_SMART',
            selectedValue: 'DEFAULT'
        )
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        DOCKER_REGISTRY = "crpi-rq074obigx0czrju.cn-chengdu.personal.cr.aliyuncs.com"
        DOCKER_NAMESPACE = "xf-spring-cloud-alibaba"
        DOCKER_CREDENTIALS_ID = "aliyun-docker-credentials"
        GITHUB_REPO = "git@github.com:RemainderTime/measure-community-platform.git"
        GITHUB_CREDENTIALS_ID = "github-ssh-key"
        DEPLOY_USER = "root"
        DEPLOY_PORT = "22"
        DEPLOY_SSH_ID = "server-ssh-credentials"
    }

    stages {
        stage('0. 自动识别服务') {
            steps {
                script {
                    // 🟢 直接从环境变量获取当前任务名
                    // 如果任务在文件夹里，JOB_NAME 可能是 "folder/community-auth"，用 split 取最后一段
                    env.REAL_SERVICE_NAME = env.JOB_NAME.split('/')[-1]

                    // 校验：确保任务名符合命名规范
                    if (!env.REAL_SERVICE_NAME.startsWith("community-")) {
                        error "任务名必须以 'community-' 开头（当前是: ${env.REAL_SERVICE_NAME}），请修改 Jenkins 任务名称！"
                    }

                    // 修改构建标题，如：#5-community-gateway
                    currentBuild.displayName = "#${BUILD_NUMBER}-${env.REAL_SERVICE_NAME}"

                    def config = getServiceConfig(env.REAL_SERVICE_NAME)
                    env.CONTAINER_NAME = config.containerName
                    env.CONTAINER_PORT = config.containerPort
                    echo "========== 自动化识别成功 =========="
                    echo "当前任务路径: ${env.JOB_NAME}"
                    echo "识别服务模块: ${env.REAL_SERVICE_NAME}"
                    echo "目标端口: ${config.containerPort}"
                    echo "===================================="
                }
            }
        }

        stage('1. 检出代码') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "${params.BRANCH_NAME}"]],
                    userRemoteConfigs: [[
                        url: env.GITHUB_REPO,
                        credentialsId: env.GITHUB_CREDENTIALS_ID
                    ]]
                ])
                script {
                    env.GIT_COMMIT_SHORT = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.BUILD_TIMESTAMP = sh(script: "date +%Y%m%d-%H%M%S", returnStdout: true).trim()
                    env.IMAGE_TAG = "${env.BUILD_TIMESTAMP}-${env.GIT_COMMIT_SHORT}"
                }
            }
        }

        // 🟢 Wave 0 功能发布门禁：unit -> integration -> system 三层齐绿才允许继续构建/推送/部署。
        // 禁止用 -DskipTests 之类的编译代替本阶段——scripts/ci/verify.sh 内部才会在测试全部通过后打包。
        stage('2. Wave 0 功能门禁验证') {
            steps {
                withCredentials([
                    usernamePassword(credentialsId: 'measure-db-credentials', usernameVariable: 'DB_USERNAME', passwordVariable: 'DB_PASSWORD'),
                    usernamePassword(credentialsId: 'measure-nacos-credentials', usernameVariable: 'NACOS_USERNAME', passwordVariable: 'NACOS_PASSWORD'),
                    string(credentialsId: 'measure-jwt-secret', variable: 'JWT_SECRET'),
                    string(credentialsId: 'measure-internal-secret', variable: 'SECURITY_INTERNAL_SECRET'),
                    string(credentialsId: 'measure-aes-key', variable: 'SENSITIVE_AES_KEY'),
                    string(credentialsId: 'measure-hmac-key', variable: 'SENSITIVE_HMAC_KEY')
                ]) {
                    sh '''
                        set -euo pipefail
                        # MySQL 的 root 密码与业务库密码一致（本地/CI 均如此，见 .env.example）
                        export MYSQL_ROOT_PASSWORD="$DB_PASSWORD"
                        # Redis 与 Nacos 服务端启动鉴权仅供本次 CI 临时栈使用，用后即焚，
                        # 不对应任何长期凭据，因此每次构建随机生成，不落库、不写入仓库。
                        export REDIS_PASSWORD="$(openssl rand -hex 24)"
                        export NACOS_AUTH_TOKEN="$(openssl rand -base64 48)"
                        export NACOS_AUTH_IDENTITY_KEY="ci-nacos-identity"
                        export NACOS_AUTH_IDENTITY_VALUE="$(openssl rand -hex 24)"
                        bash scripts/ci/verify.sh
                    '''
                }
            }
        }

        stage('3. 构建与推送镜像') {
            steps {
                script {
                    def config = getServiceConfig(env.REAL_SERVICE_NAME)
                    def FULL_IMAGE_NAME = "${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${config.imageName}"
                    env.FULL_IMAGE_NAME = FULL_IMAGE_NAME

                    withCredentials([usernamePassword(
                        credentialsId: "${DOCKER_CREDENTIALS_ID}",
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )]) {
                        sh "echo \${DOCKER_PASS} | docker login -u \${DOCKER_USER} --password-stdin ${DOCKER_REGISTRY}"
                        sh "docker build --build-arg SERVICE_NAME=${env.REAL_SERVICE_NAME} -t ${FULL_IMAGE_NAME}:${env.IMAGE_TAG} -t ${FULL_IMAGE_NAME}:latest ."
                        sh "docker push ${FULL_IMAGE_NAME}:${env.IMAGE_TAG}"
                        sh "docker push ${FULL_IMAGE_NAME}:latest"
                    }
                }
            }
        }

        stage('4. 远程部署') {
            steps {
                // 任一凭据缺失，withCredentials 会直接让本阶段失败，不会以裸容器方式启动
                withCredentials([
                    usernamePassword(credentialsId: 'measure-db-credentials', usernameVariable: 'DB_USERNAME', passwordVariable: 'DB_PASSWORD'),
                    usernamePassword(credentialsId: 'measure-nacos-credentials', usernameVariable: 'NACOS_USERNAME', passwordVariable: 'NACOS_PWD'),
                    string(credentialsId: 'measure-jwt-secret', variable: 'JWT_SECRET'),
                    string(credentialsId: 'measure-internal-secret', variable: 'SECURITY_INTERNAL_SECRET'),
                    string(credentialsId: 'measure-aes-key', variable: 'SENSITIVE_AES_KEY'),
                    string(credentialsId: 'measure-hmac-key', variable: 'SENSITIVE_HMAC_KEY'),
                    string(credentialsId: 'measure-deploy-host', variable: 'DEPLOY_HOST')
                ]) {
                    sshagent(["${DEPLOY_SSH_ID}"]) {
                        sh '''
                            set -euo pipefail
                            ssh -o StrictHostKeyChecking=no -p "$DEPLOY_PORT" "$DEPLOY_USER@$DEPLOY_HOST" << DEPLOY_SCRIPT
set -e
docker stop $CONTAINER_NAME || true
docker rm $CONTAINER_NAME || true
docker pull $FULL_IMAGE_NAME:$IMAGE_TAG
docker run -d --name $CONTAINER_NAME -p $CONTAINER_PORT:8080 --restart=always -m 512m -e JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC" -e SPRING_PROFILES_ACTIVE=prod -e NACOS_SERVER_ADDR=117.72.35.70 -e NACOS_USERNAME="$NACOS_USERNAME" -e NACOS_PWD="$NACOS_PWD" -e DB_USERNAME="$DB_USERNAME" -e DB_PASSWORD="$DB_PASSWORD" -e JWT_SECRET="$JWT_SECRET" -e SECURITY_INTERNAL_SECRET="$SECURITY_INTERNAL_SECRET" -e SENSITIVE_AES_KEY="$SENSITIVE_AES_KEY" -e SENSITIVE_HMAC_KEY="$SENSITIVE_HMAC_KEY" $FULL_IMAGE_NAME:$IMAGE_TAG

# 保持远程服务器整洁，保留最近 3 个版本的镜像
docker images $FULL_IMAGE_NAME --format "{{.ID}}" | tail -n +4 | xargs -r docker rmi -f || true
DEPLOY_SCRIPT
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            // 🟢 收集单元/集成测试报告，便于失败时追溯
            junit testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml', allowEmptyResults: true
            // 🟢 本地资源清理（确保 Wave 0 门禁临时栈与宿主机镜像不会越积越多）
            sh '''
                docker compose --profile app down -v || true
                docker image prune -f || true
                docker builder prune -f || true
            '''
        }
    }
}

def getServiceConfig(serviceName) {
    def config = [:]
    switch(serviceName) {
        case 'community-gateway':  config.containerName = 'community-gateway';  config.containerPort = '9090'; config.imageName = 'community-gateway'; break
        case 'community-auth':     config.containerName = 'community-auth';     config.containerPort = '9093'; config.imageName = 'community-auth'; break
        case 'community-info':    config.containerName = 'community-info';    config.containerPort = '9094'; config.imageName = 'community-info'; break
        default: error("未定义的服务映射: ${serviceName}。请检查 getServiceConfig 函数。")
    }
    return config
}
