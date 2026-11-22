package com.example.weather_order_assistant_system.client;

import java.util.Map;

/**
 * 中央氣象署縣市與天氣預報資料集對應類別。
 *
 * 負責將使用者選擇的縣市名稱，
 * 轉換成中央氣象署未來 1 週逐 12 小時天氣預報資料集代碼。
 *
 * 例如：
 * 高雄市 -> F-D0047-067
 * 臺南市 -> F-D0047-079
 *
 * 此類別只負責資料集代碼對應，
 * 不負責發送 HTTP 請求或解析天氣 JSON。
 *
 * 第一版直接使用 Map 維護資料，
 * 不需要新增 MySQL 資料表。
 */
public final class CwaDatasetMapping {

    // 私有建構子，避免此工具類別被建立實例
    private CwaDatasetMapping() {
    }

    // 全臺 22 縣市的未來 1 週逐 12 小時天氣預報資料集代碼
    //
    // 使用 Map.ofEntries 建立不可修改的對應資料。
    // 縣市名稱採用中央氣象署官方名稱。
    private static final Map<String, String> DATASET_MAPPING =
            Map.ofEntries(

                    // 北部地區
                    Map.entry("臺北市", "F-D0047-063"),
                    Map.entry("新北市", "F-D0047-071"),
                    Map.entry("基隆市", "F-D0047-051"),
                    Map.entry("桃園市", "F-D0047-007"),
                    Map.entry("新竹市", "F-D0047-055"),
                    Map.entry("新竹縣", "F-D0047-011"),
                    Map.entry("宜蘭縣", "F-D0047-003"),

                    // 中部地區
                    Map.entry("臺中市", "F-D0047-075"),
                    Map.entry("苗栗縣", "F-D0047-015"),
                    Map.entry("彰化縣", "F-D0047-019"),
                    Map.entry("南投縣", "F-D0047-023"),
                    Map.entry("雲林縣", "F-D0047-027"),

                    // 南部地區
                    Map.entry("臺南市", "F-D0047-079"),
                    Map.entry("高雄市", "F-D0047-067"),
                    Map.entry("嘉義市", "F-D0047-059"),
                    Map.entry("嘉義縣", "F-D0047-031"),
                    Map.entry("屏東縣", "F-D0047-035"),

                    // 東部地區
                    Map.entry("花蓮縣", "F-D0047-043"),
                    Map.entry("臺東縣", "F-D0047-039"),

                    // 離島地區
                    Map.entry("澎湖縣", "F-D0047-047"),
                    Map.entry("金門縣", "F-D0047-087"),
                    Map.entry("連江縣", "F-D0047-083")
            );

    /**
     * 根據縣市名稱取得中央氣象署資料集代碼。
     *
     * @param city 縣市完整名稱，例如：高雄市
     * @return 對應的未來 1 週逐 12 小時天氣預報資料集代碼
     */
    public static String getDatasetId(String city) {

        // 檢查縣市名稱是否為空
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("縣市名稱不可為空");
        }

        // 使用完整名稱查詢，不進行模糊比對
        String datasetId = DATASET_MAPPING.get(city);

        // 如果查不到對應資料集，代表不是支援的官方縣市名稱
        if (datasetId == null) {
            throw new IllegalArgumentException(
                    "不支援的縣市名稱：" + city
            );
        }

        return datasetId;
    }

    /**
     * 取得目前支援的縣市名稱集合。
     *
     * 未來可以提供給前端縣市下拉選單使用。
     *
     * @return 所有支援的縣市名稱
     */
    public static java.util.Set<String> getSupportedCities() {
        return DATASET_MAPPING.keySet();
    }
}
