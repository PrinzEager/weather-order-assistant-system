# 使用本機 Maven 事先編譯的 Spring Boot JAR，不在 Docker 建置階段重跑 Maven。
# 必須先執行：.\mvnw.cmd clean package （Windows PowerShell）
FROM eclipse-temurin:21-jre

# Java 程式與商品圖片預設儲存目錄
WORKDIR /app
RUN groupadd --system appuser \
    && useradd --system --gid appuser --home-dir /app appuser \
    && mkdir -p /app/uploads/products \
    && chown -R appuser:appuser /app

# Maven POM：artifactId=weather-order-assistant-system、version=0.0.1-SNAPSHOT
COPY --chown=appuser:appuser target/weather-order-assistant-system-0.0.1-SNAPSHOT.jar /app/app.jar

# 以非 root 身分執行，避免給應用程式過高的容器權限。
USER appuser
ENV TZ=Asia/Taipei
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
