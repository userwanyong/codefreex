# ==================== Stage 1: Build ====================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# 将本地 SNAPSHOT 依赖复制到容器 Maven 仓库
COPY local-libs/ /root/.m2/repository/cn/wanyj/auth/

COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -B

# ==================== Stage 2: Runtime ====================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 安装 Chromium + ChromeDriver + Node.js (npm)
RUN apk add --no-cache chromium chromium-chromedriver nodejs npm \
    && addgroup -S app && adduser -S app -G app

COPY --from=builder /app/target/*.jar app.jar

RUN mkdir -p /app/logs /app/tmp/code_output /app/tmp/code_deploy /app/tmp/code_download /app/tmp/nginx \
    && chown -R app:app /app/logs /app/tmp

USER app

EXPOSE 8123

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
