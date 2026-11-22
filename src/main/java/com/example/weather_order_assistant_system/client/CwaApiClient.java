package com.example.weather_order_assistant_system.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 中央氣象署 API 客戶端。
 *
 * 負責透過 HTTP 取得中央氣象署的原始天氣預報 JSON。
 *
 * 此類別只負責：
 * 1. 建立 API 請求。
 * 2. 帶入授權碼。
 * 3. 指定天氣預報資料集與鄉鎮市區。
 * 4. 回傳原始 JSON。
 *
 * 不負責 JSON 解析、日期篩選或天氣倍率計算。
 * 以上邏輯將交由後續 WeatherService 處理。
 */
@Component
public class CwaApiClient {

    // Spring RestClient，負責發送 HTTP 請求
    private final RestClient restClient;

    // 中央氣象署 API 授權碼
    // 從 application.properties 取得，不寫死在程式碼中
    private final String authorization;

    /**
     * 建立中央氣象署 API Client。
     *
     * baseUrl 與 authorization 均由設定檔注入。
     */
    public CwaApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${cwa.api.base-url}") String baseUrl,
            @Value("${cwa.api.authorization}") String authorization,
            @Value("${cwa.api.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${cwa.api.read-timeout-ms:10000}") long readTimeoutMs) {

        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("天氣資料連線及讀取逾時必須大於 0");
        }

        // 連線逾時用於建立 TCP 連線；讀取逾時限制單次請求等待時間。
        // 限定在 CWA 客戶端，不影響其他 RestClient 的網路設定。
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        // 保存 API 授權碼
        this.authorization = authorization;
    }

    /**
     * 取得指定鄉鎮市區的天氣預報原始 JSON。
     *
     * @param datasetId 中央氣象署資料集代碼
     *                  例如：F-D0047-065
     * @param district  鄉鎮市區完整名稱
     *                  例如：路竹區
     * @return 中央氣象署回傳的原始 JSON 字串
     */
    public String getForecastJson(
            String datasetId,
            String district) {

        // 檢查資料集代碼是否符合預期格式
        // 避免任意路徑進入 API 請求
        if (datasetId == null
                || !datasetId.matches("F-D0047-\\d{3}")) {
            throw new IllegalArgumentException("天氣預報資料集代碼格式不正確");
        }

        // 檢查行政區名稱不可為空
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("鄉鎮市區名稱不可為空");
        }

        // 發送 GET 請求
        // Authorization：中央氣象署授權碼
        // locationName：指定鄉鎮市區
        // format：指定回傳 JSON 格式
        String json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(datasetId)
                        .queryParam("Authorization", authorization)
                        .queryParam("locationName", district)
                        .queryParam("format", "JSON")
                        .build())
                .retrieve()
                .body(String.class);

        // 避免將空回應當作有效的天氣預報資料
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("中央氣象署 API 回傳空白資料");
        }

        // 回傳原始 JSON，供後續 WeatherService 解析
        return json;
    }
}
