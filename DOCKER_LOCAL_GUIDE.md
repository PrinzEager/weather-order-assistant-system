# 本機 Docker 第 1 階段｜weather-order-assistant-system

本次僅新增 Docker 設定，不修改 Java、Maven、Flyway Migration 或現有 application.properties。參考使用者提供的 GitHub 範例：MySQL 8.4 Docker Compose，以及 JAR 執行於 Java 21 JRE 容器。

## 一、前置條件

- Windows：Docker Desktop 已啟動且 `docker version`、`docker compose version` 正常。
- 已安裝 JDK 21；專案提供 `mvnw.cmd`，不需要額外安裝 Maven。
- 在專案根目錄（與 `pom.xml` 同層）執行下列 PowerShell 指令。

## 二、先用 Maven 打包 JAR

```powershell
.\mvnw.cmd clean package
Get-Item .\target\weather-order-assistant-system-0.0.1-SNAPSHOT.jar
```

第一次先保留單元測試。若測試失敗，請先查看測試原因；純為隔離打包問題，才暫時使用 `-DskipTests`，不能把跳過測試當成 CI/CD 的預設。

## 三、建立本機環境變數

```powershell
Copy-Item .env.docker.example .env.docker
notepad .env.docker
```

請修改 MySQL 密碼、管理員密碼（至少 12 字元）、CWA API Key。本機 Docker MySQL 是另一套**全新資料庫**，不會自動搬移 Windows 原有 MySQL 的資料。`mysql` 服務名稱即容器間連線的主機名稱，切勿把 Docker 內的 DB_URL 改回 `localhost`。

**請先確認 `.env.docker` 已被 `.gitignore` 排除。**

## 四、確認設定並建構 Docker Image

```powershell
docker compose --env-file .env.docker config --quiet
docker compose --env-file .env.docker build app
docker image ls weather-order-assistant
```

`docker compose config` 不加 `--quiet` 可能輸出已展開的密碼，請勿將完整內容貼到公開平台。

## 五、啟動 MySQL 和 Spring Boot

```powershell
docker compose --env-file .env.docker up -d
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker logs --tail=100 app
```

測試：

```powershell
curl.exe -i http://localhost:8081/actuator/health/readiness
curl.exe -I http://localhost:8081/login
```

`/actuator/health/readiness` 預期是 HTTP 200、包含 `"status":"UP"`；`/login` 預期可開啟登入頁。若 HEAD（`-I`）不被支援，改用 `curl.exe -i http://localhost:8081/login`。

登入網址：`http://localhost:8081/login`。

## 六、建立管理員後，關閉首次初始化

成功登入並完成初始化後，把 `.env.docker` 中 `APP_ADMIN_BOOTSTRAP=false`，並清空 `APP_ADMIN_PASSWORD`，再執行：

```powershell
docker compose --env-file .env.docker up -d --force-recreate app
```

容器會重新啟動，但 MySQL 命名 Volume 不會被刪除。

## 七、停止、重啟、查看資料

```powershell
docker compose --env-file .env.docker down
docker compose --env-file .env.docker up -d
docker compose --env-file .env.docker logs -f app
```

**禁止在有需要保留的資料時執行 `docker compose down -v`**，該指令會刪除 MySQL 與商品檔案的 Volume。需要重新打包時，先 `.\mvnw.cmd clean package`，再 `docker compose --env-file .env.docker up -d --build --force-recreate app`。

## 常見排錯

- `COPY ... jar: not found`：先執行 Maven package，確認 JAR 路徑。
- `Port is already allocated`：修改 `.env.docker` 的 `APP_HOST_PORT` 或 `MYSQL_HOST_PORT`。
- `Communications link failure`：MySQL 容器還沒啟動完成、登入資訊錯誤，或 DB_URL 誤用 localhost。
- `Access denied for user`：MySQL Volume 若已初始化，更改 `.env.docker` 的 `MYSQL_USER/PASSWORD` 不會自動更新既有資料庫帳號，請先檢查，而非直接刪除 Volume。
- Flyway migration 驗證失敗：貼上不含密碼的完整錯誤訊息，不要任意修改已執行的 V001～V011。
- 登入後無法保留 Session：本機 HTTP 需要確認 Compose 的 `SERVER_SERVLET_SESSION_COOKIE_SECURE=false`；正式 HTTPS 部署則必須使用 Secure Cookie。
- Image 建立成功但容器未啟動：`docker compose --env-file .env.docker logs --tail=150 app`。

## 與 EC2 範例的關係

範例 EC2 Compose 是把主機上的 `target/app.jar` 掛進 `eclipse-temurin:21-jre` 容器；此版本則以 `Dockerfile` 把編譯後 JAR 複製進 `weather-order-assistant:local` Image，方便熟悉 `docker build`。兩者都使用 Java 21、Spring Boot JAR 和獨立 MySQL。EC2 最終部署仍需補足 HTTPS、Secret 安全管理與 CI/CD，這份本機設定不可原封不動公開到網路。
