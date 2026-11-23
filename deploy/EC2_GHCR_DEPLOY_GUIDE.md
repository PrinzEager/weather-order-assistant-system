# weather-order-assistant-system：AWS EC2 直接從 GHCR 部署

## 前置狀態

- 2026-11-23 GitHub Actions 測試及 GHCR 手動發布通過；GHCR Image 已在本機下載、啟動及登入驗收。
- EC2 Ubuntu、Docker 29.9.2、Compose v5.7.0；約 1.9 GiB RAM，無 Swap，磁碟 58 GB。
- 此版本不更動 EC2 規格、不增 EBS、不建 RDS、不啟用 GitHub Actions CD。
- 第一輪使用**本機綁定 + SSH Tunnel** 驗收；並不是公開網站的 HTTPS 正式部署。
- 在 EC2 上建立**新的** MySQL Volume。原本本機 MySQL 的測試資料不會自動出現在 EC2。

## 1. EC2：確認 Docker 與是否已有執行中的容器

在 EC2 Ubuntu SSH 終端機執行：

```bash
docker ps -a
docker volume ls
ss -lnt | grep ':8080 ' || true
free -h
df -h
mkdir -p ~/weather-order-deploy
```

若有既有容器使用 8080 或已有重要資料，先停止，不要覆蓋或刪除。**不要執行 docker system prune --volumes 或 docker compose down -v。**

## 2. Windows：上傳兩份非秘密設定檔

在已下載補丁、能使用 `.pem` SSH 私鑰的 Windows PowerShell 執行（替換金鑰路徑與 EC2 公網 IP）：

```powershell
scp -i "C:\path\your-ec2-key.pem" .\compose.ec2.yaml .\.env.ec2.example ubuntu@<EC2_PUBLIC_IP>:~/weather-order-deploy/
```

若透過 EC2 Console 的瀏覽器終端機且無法使用 SCP，也可以在 EC2 的 `~/weather-order-deploy/` 使用 `nano compose.ec2.yaml` 與 `nano .env.ec2.example` 貼上補丁內容。不要上傳含本機密碼的 `.env.docker`。

## 3. EC2：建立專用的秘密環境檔

```bash
cd ~/weather-order-deploy
cp .env.ec2.example .env.ec2
chmod 600 .env.ec2
nano .env.ec2
```

將三個 `REPLACE_ME_...` 改成**三組不同**的強密碼（建議至少 20 字元，可在 EC2 用 `openssl rand -hex 24` 生成三次）；如需天氣功能填入 `CWA_API_KEY`。不要把任何密碼/Key/Token 傳到對話或 GitHub。

驗證沒有未替換的佔位值：

```bash
if grep -q 'REPLACE_ME' .env.ec2; then echo '請先修改環境檔中的 REPLACE_ME'; else echo '密碼佔位值已處理'; fi
```

## 4. EC2：登入私人 GHCR

若 GHCR Package 為 Private，建立有 `read:packages` 權限、能讀取該 Package 的 GitHub Personal Access Token (classic)。**請在 SSH 終端機輸入，不要把 Token 寫入檔案或指令歷史。**

```bash
read -r -s -p 'GitHub PAT (read:packages): ' GHCR_TOKEN; echo
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u PrinzEager --password-stdin
unset GHCR_TOKEN
```

`docker login` 成功後，Docker 可能把登入資訊存於 `~/.docker/config.json`；請限制權限：

```bash
chmod 700 ~/.docker
chmod 600 ~/.docker/config.json
```

如果 GHCR Package 為 Public，通常可以直接 pull 而不登入。若 `denied` 或 `unauthorized`，請檢查 Token 權限、Package 可讀性和名稱。參考 GitHub Docs：https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry

## 5. EC2：下載 Image、啟動 MySQL 和 Spring Boot

所有指令在 `~/weather-order-deploy`：

```bash
docker compose --env-file .env.ec2 -f compose.ec2.yaml config --quiet
docker compose --env-file .env.ec2 -f compose.ec2.yaml pull

docker compose --env-file .env.ec2 -f compose.ec2.yaml up -d mysql
docker compose --env-file .env.ec2 -f compose.ec2.yaml ps

docker compose --env-file .env.ec2 -f compose.ec2.yaml up -d app
docker compose --env-file .env.ec2 -f compose.ec2.yaml ps
```

若 `mysql` 長時間不健康，先用 `logs --tail=100 mysql` 查明原因。EC2 只有約 2 GiB RAM 且沒有 Swap：Compose 設定讓 MySQL 使用較小的 InnoDB buffer pool，JVM Heap 上限 320 MiB，並設定容器記憶體限制，仍可能因負載或啟動尖峰記憶體不足。

檢查日誌（可能有業務資訊，分享前先遮罩敏感值）：

```bash
docker compose --env-file .env.ec2 -f compose.ec2.yaml logs --tail=100 mysql
docker compose --env-file .env.ec2 -f compose.ec2.yaml logs --tail=150 app
```

## 6. EC2：驗證內部 HTTP 健康檢查

```bash
curl -i http://127.0.0.1:8080/actuator/health/readiness
curl -sS -o /dev/null -w 'login HTTP=%{http_code}\n' http://127.0.0.1:8080/login
```

預期 readiness HTTP 200，回傳 JSON `{"status":"UP"}`；登入頁 HTTP 200。若發生 JPA schema validation，切勿修改或刪除既有 Flyway Migration 或 Volume，請先看 log。

## 7. Windows：開啟 SSH Tunnel 並登入

在 Windows 新的 PowerShell 視窗（須保留 SSH 連線開啟）輸入：

```powershell
ssh -i "C:\path\your-ec2-key.pem" -N -L 127.0.0.1:8083:127.0.0.1:8080 ubuntu@<EC2_PUBLIC_IP>
```

開啟 `http://localhost:8083/login` 並使用步驟 3 設定的管理員帳密登入，確認能進入 `http://localhost:8083/home`。

此時網站僅能從可建立 SSH Tunnel 的電腦瀏覽，**不是對所有網路使用者公開的 HTTPS 網站**。不需要修改 EC2 Security Group 放行 8080/3306。

## 8. EC2：首次登入後停用管理員初始化

```bash
cd ~/weather-order-deploy
nano .env.ec2
```

修改：

```properties
APP_ADMIN_BOOTSTRAP=false
APP_ADMIN_PASSWORD=
```

儲存後只重建 app（不重建 MySQL）：

```bash
docker compose --env-file .env.ec2 -f compose.ec2.yaml up -d --no-deps --force-recreate app
```

再次透過 SSH Tunnel 登入。`appuser` 已由映像檔建立，商品圖片位於命名 Volume 中。

## 9. 停止 / 重啟 / 安全注意

```bash
# 停止（保留資料）
docker compose --env-file .env.ec2 -f compose.ec2.yaml stop
# 重啟
docker compose --env-file .env.ec2 -f compose.ec2.yaml up -d
```

**不要使用 `down -v`**：這會刪除 MySQL 與商品圖片 Volume。另請記得同一台 EC2 上的 Volume 不是異地備份；未來需增加備份與還原演練。使用 `docker volume ls` 可查看 Volume。

## 10. 故障排除

- `permission denied /var/run/docker.sock`：使用者尚未有 Docker 權限；使用與先前 `docker --version` 不同的環境，需釐清 sudo / Docker group。不要未經確認大量修改主機權限。
- `denied` / `unauthorized`（GHCR）：檢查 PAT classic `read:packages`、套件存取權限及登入的 Ubuntu 帳號。
- `mysql unhealthy`：檢查密碼、Volume 狀態、資源限制及 MySQL logs。
- `app Exited (137)` 或 `OOMKilled`：記憶體不足；先取 `docker inspect`、`free -h` 和 log 分析，別直接改 EC2 規格。
- `Access denied for user`：不要靠更改 `.env.ec2` 密碼修復已有資料的 MySQL；MySQL 初始化後密碼不會自動同步。
- `Could not connect to DB`：容器中的 JDBC host 必須是 `mysql`，不是 `localhost`。
- 在 EC2 用 `curl` 成功但 Windows 無法打開 8083：檢查 SSH Tunnel 是否仍在執行、Windows 本地 8083 是否已被占用。
- 跳回登入頁：在本輪 SSH Tunnel 的 HTTP 測試設定 Cookie Secure=false；若改成 HTTPS，務必移除覆寫並啟用 Secure Cookie。

## 後續才做：公開網站與 GitHub Actions CD

目前沒有公開 8080／3306。未來要對外提供網站，先設定網域、HTTPS 反向代理、正確的 Forwarded Headers／Session Cookie 與 AWS Security Group，然後才銜接 Actions 自動更新容器，並加入回滾與資料庫備份策略。

**費用提醒**：EC2 持續執行、EBS 與可能的資料傳輸仍可能產生 AWS 帳單；是否仍在免費試用額度內要在 AWS Billing 自行確認。
