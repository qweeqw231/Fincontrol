# ============================================
# FinControl Backend Dockerfile（multi-stage）
# ============================================
# Stage 1: build jar with Maven + JDK 17
# Stage 2: runtime with JRE 17 only (小镜像)
# ============================================
# 构建：  docker build -t fincontrol-backend:dev -f Dockerfile ..
# 启动：  docker run --rm -p 8080:8080 \
#           -e DB_HOST=mysql -e DB_PORT=3306 \
#           -e DB_USER=root -e DB_PASSWORD=root \
#           fincontrol-backend:dev

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先拷贝 pom 让依赖层可复用（pom 不变就不重下）
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

# 再拷贝源码打包（排除测试加速）
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 非 root 用户运行
RUN groupadd -r app && useradd -r -g app app

# 从 builder 拷贝可执行 jar
COPY --from=builder /build/target/*.jar /app/app.jar

# 上传目录（容器内）
RUN mkdir -p /app/uploads/screenshots && chown -R app:app /app
USER app

EXPOSE 8080

# 健康检查：actuator/health
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

# JVM 参数（容器友好）
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]