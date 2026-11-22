/**
 * 臺灣縣市與鄉鎮市區對照表。
 * 此檔案只負責行政區選單，不處理天氣 API 與訂貨邏輯。
 * 名稱採「臺」字寫法，確保傳送至後端時與中央氣象署資料相符。
 * 正式使用前可依內政部國土測繪中心的鄉鎮市區清單進行更新。
 */
export const TAIWAN_DISTRICTS = Object.freeze({
    "臺北市": "松山區 信義區 大安區 中山區 中正區 大同區 萬華區 文山區 南港區 內湖區 士林區 北投區".split(" "),
    "新北市": "板橋區 三重區 中和區 永和區 新莊區 新店區 土城區 蘆洲區 樹林區 鶯歌區 三峽區 淡水區 汐止區 瑞芳區 五股區 泰山區 林口區 深坑區 石碇區 坪林區 三芝區 石門區 八里區 平溪區 雙溪區 貢寮區 金山區 萬里區 烏來區".split(" "),
    "桃園市": "桃園區 中壢區 平鎮區 八德區 楊梅區 蘆竹區 大溪區 龜山區 大園區 觀音區 新屋區 龍潭區 復興區".split(" "),
    "臺中市": "中區 東區 南區 西區 北區 北屯區 西屯區 南屯區 太平區 大里區 霧峰區 烏日區 豐原區 后里區 石岡區 東勢區 和平區 新社區 潭子區 大雅區 神岡區 大肚區 沙鹿區 龍井區 梧棲區 清水區 大甲區 外埔區 大安區".split(" "),
    "臺南市": "中西區 東區 南區 北區 安平區 安南區 永康區 歸仁區 新化區 左鎮區 玉井區 楠西區 南化區 仁德區 關廟區 龍崎區 官田區 麻豆區 佳里區 西港區 七股區 將軍區 學甲區 北門區 新營區 後壁區 白河區 東山區 六甲區 下營區 柳營區 鹽水區 善化區 大內區 山上區 新市區 安定區".split(" "),
    "高雄市": "新興區 前金區 苓雅區 鹽埕區 鼓山區 旗津區 前鎮區 三民區 楠梓區 小港區 左營區 鳳山區 林園區 大寮區 大樹區 大社區 仁武區 鳥松區 岡山區 橋頭區 燕巢區 田寮區 阿蓮區 路竹區 湖內區 茄萣區 永安區 彌陀區 梓官區 旗山區 美濃區 六龜區 甲仙區 杉林區 內門區 茂林區 桃源區 那瑪夏區".split(" "),
    "基隆市": "仁愛區 信義區 中正區 中山區 安樂區 暖暖區 七堵區".split(" "),
    "新竹市": "東區 北區 香山區".split(" "),
    "嘉義市": "東區 西區".split(" "),
    "宜蘭縣": "宜蘭市 羅東鎮 蘇澳鎮 頭城鎮 礁溪鄉 壯圍鄉 員山鄉 冬山鄉 五結鄉 三星鄉 大同鄉 南澳鄉".split(" "),
    "新竹縣": "竹北市 竹東鎮 新埔鎮 關西鎮 湖口鄉 新豐鄉 芎林鄉 橫山鄉 北埔鄉 寶山鄉 峨眉鄉 尖石鄉 五峰鄉".split(" "),
    "苗栗縣": "苗栗市 頭份市 竹南鎮 後龍鎮 通霄鎮 苑裡鎮 卓蘭鎮 造橋鄉 西湖鄉 頭屋鄉 公館鄉 銅鑼鄉 三義鄉 大湖鄉 獅潭鄉 三灣鄉 南庄鄉 泰安鄉".split(" "),
    "彰化縣": "彰化市 員林市 和美鎮 鹿港鎮 溪湖鎮 二林鎮 田中鎮 北斗鎮 花壇鄉 芬園鄉 大村鄉 埔鹽鄉 埔心鄉 永靖鄉 社頭鄉 田尾鄉 埤頭鄉 芳苑鄉 大城鄉 竹塘鄉 溪州鄉 線西鄉 伸港鄉 福興鄉 秀水鄉 二水鄉".split(" "),
    "南投縣": "南投市 埔里鎮 草屯鎮 竹山鎮 集集鎮 名間鄉 鹿谷鄉 中寮鄉 魚池鄉 國姓鄉 水里鄉 信義鄉 仁愛鄉".split(" "),
    "雲林縣": "斗六市 斗南鎮 虎尾鎮 西螺鎮 土庫鎮 北港鎮 古坑鄉 大埤鄉 莿桐鄉 林內鄉 二崙鄉 崙背鄉 麥寮鄉 東勢鄉 褒忠鄉 臺西鄉 元長鄉 四湖鄉 口湖鄉 水林鄉".split(" "),
    "嘉義縣": "太保市 朴子市 布袋鎮 大林鎮 民雄鄉 溪口鄉 新港鄉 六腳鄉 東石鄉 義竹鄉 鹿草鄉 水上鄉 中埔鄉 竹崎鄉 梅山鄉 番路鄉 大埔鄉 阿里山鄉".split(" "),
    "屏東縣": "屏東市 潮州鎮 東港鎮 恆春鎮 萬丹鄉 長治鄉 麟洛鄉 九如鄉 里港鄉 鹽埔鄉 高樹鄉 萬巒鄉 內埔鄉 竹田鄉 新埤鄉 枋寮鄉 新園鄉 崁頂鄉 林邊鄉 南州鄉 佳冬鄉 琉球鄉 車城鄉 滿州鄉 枋山鄉 三地門鄉 霧臺鄉 瑪家鄉 泰武鄉 來義鄉 春日鄉 獅子鄉 牡丹鄉".split(" "),
    "臺東縣": "臺東市 成功鎮 關山鎮 卑南鄉 鹿野鄉 池上鄉 東河鄉 長濱鄉 太麻里鄉 大武鄉 綠島鄉 海端鄉 延平鄉 金峰鄉 達仁鄉 蘭嶼鄉".split(" "),
    "花蓮縣": "花蓮市 鳳林鎮 玉里鎮 新城鄉 吉安鄉 壽豐鄉 光復鄉 豐濱鄉 瑞穗鄉 富里鄉 秀林鄉 萬榮鄉 卓溪鄉".split(" "),
    "澎湖縣": "馬公市 湖西鄉 白沙鄉 西嶼鄉 望安鄉 七美鄉".split(" "),
    "金門縣": "金城鎮 金湖鎮 金沙鎮 金寧鄉 烈嶼鄉 烏坵鄉".split(" "),
    "連江縣": "南竿鄉 北竿鄉 莒光鄉 東引鄉".split(" ")
});

/**
 * 將已存在的「縣市」與「鄉鎮市區」select 綁定為連動選單。
 * prefix 可傳入 weather 或 recommendation，與既有 DOM id 一致。
 */
export function initLocationDropdown(prefix) {
    const citySelect = document.getElementById(`${prefix}-city`);
    const districtSelect = document.getElementById(`${prefix}-district`);

    if (!citySelect || !districtSelect) {
        console.warn(`找不到 ${prefix} 的縣市或鄉鎮市區選單`);
        return;
    }

    // 避免初始化函式重複執行，造成 change 監聽器重複註冊。
    if (citySelect.dataset.locationInitialized === "true")
        return;
    citySelect.dataset.locationInitialized = "true";

    const originalCity = citySelect.value || "高雄市";
    const originalDistrict = districtSelect.value || "路竹區";

    citySelect.replaceChildren(new Option("請選擇縣市", ""));
    Object.keys(TAIWAN_DISTRICTS).forEach(city => {
        citySelect.add(new Option(city, city));
    });

    // 選擇不同縣市時，必須清掉上次的鄉鎮，不能沿用舊縣市的資料。
    function updateDistricts(preferredDistrict = "") {
        districtSelect.replaceChildren(new Option("請選擇鄉鎮市區", ""));
        const options = TAIWAN_DISTRICTS[citySelect.value] ?? [];
        options.forEach(district => {
            districtSelect.add(new Option(district, district));
        });
        districtSelect.disabled = options.length === 0;
        districtSelect.value = options.includes(preferredDistrict) ? preferredDistrict : "";
    }

    citySelect.value = Object.hasOwn(TAIWAN_DISTRICTS, originalCity)
        ? originalCity : "";
    updateDistricts(originalDistrict);

    citySelect.addEventListener("change", () => updateDistricts());
}
