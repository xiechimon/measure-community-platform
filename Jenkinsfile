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
            defaultValue: 'master',
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
        DEPLOY_HOST = "服务器ip"
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
                    if (!env.REAL_SERVICE_NAME.startsWith("cloud-")) {
                        error "任务名必须以 'cloud-' 开头（当前是: ${env.REAL_SERVICE_NAME}），请修改 Jenkins 任务名称！"
                    }

                    // 修改构建标题，如：#5-community-gateway
                    currentBuild.displayName = "#${BUILD_NUMBER}-${env.REAL_SERVICE_NAME}"

                    def config = getServiceConfig(env.REAL_SERVICE_NAME)
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

        stage('2. Maven 编译') {
            steps {
                script {
                    // 🟢 增量编译识别出的模块
                    sh "mvn install -DskipTests --fail-at-end -pl ${env.REAL_SERVICE_NAME} -am -Dmaven.repo.local=/root/.m2/repository"
                }
            }
        }

        stage('3. 构建与推送镜像') {
            steps {
                script {
                    def config = getServiceConfig(env.REAL_SERVICE_NAME)
                    def FULL_IMAGE_NAME = "${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${config.imageName}"

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
                script {
                    def config = getServiceConfig(env.REAL_SERVICE_NAME)
                    def FULL_IMAGE_NAME = "${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${config.imageName}"

                    sshagent(["${DEPLOY_SSH_ID}"]) {
                        sh '''
                            ssh -o StrictHostKeyChecking=no -p ''' + DEPLOY_PORT + ' ' + DEPLOY_USER + '@' + DEPLOY_HOST + ''' << 'DEPLOY_SCRIPT'
                                set -e
                                CONTAINER_NAME="''' + config.containerName + '''"
                                CONTAINER_PORT="''' + config.containerPort + '''"
                                FULL_IMAGE_NAME="''' + FULL_IMAGE_NAME + '''"
                                IMAGE_TAG="''' + env.IMAGE_TAG + '''"

                                docker stop \${CONTAINER_NAME} || true
                                docker rm \${CONTAINER_NAME} || true
                                docker pull \${FULL_IMAGE_NAME}:\${IMAGE_TAG}

                                docker run -d \\
                                  --name \${CONTAINER_NAME} \\
                                  -p \${CONTAINER_PORT}:8080 \\
                                  --restart=always \\
                                  -m 512m \\
                                  -e JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC" \\
                                  -e NACOS_SERVER_ADDR=117.72.35.70 \\
                                  -e NACOS_USERNAME=nacos \\
                                  -e NACOS_PWD=nacos \\
                                  \${FULL_IMAGE_NAME}:\${IMAGE_TAG}

                                # 保持远程服务器整洁，保留最近 3 个版本的镜像
                                docker images \${FULL_IMAGE_NAME} --format "{{.ID}}" | tail -n +4 | xargs -r docker rmi -f || true
DEPLOY_SCRIPT
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            // 🟢 本地资源清理（确保 2核3G 宿主机不会因为频繁构建而磁盘爆炸）
            sh '''
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
