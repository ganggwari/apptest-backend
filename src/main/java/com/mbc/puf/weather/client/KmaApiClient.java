package com.mbc.puf.weather.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.weather.entity.WeatherArea;
import com.mbc.puf.weather.service.KmaClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 기상청 호출 (공공데이터포털 기상청 단기예보 조회서비스). 프런트 날씨 화면 표출 전용이다.
 * 침수모델(FastAPI)이 읽는 weather / weather_forecast 와는 완전히 별개이고(그쪽은 시연 stub 그대로),
 * 여기서 받은 값은 kma_weather / kma_forecast 에만 저장된다. KmaClient(stub 쪽 인터페이스)는 구현하지 않는다.
 *  - 현재 날씨 : 초단기실황(getUltraSrtNcst)  - 기온·습도·1시간 강수량, 매시 정각 기준
 *  - 예보      : 초단기예보(getUltraSrtFcst)  - 앞으로 6시간(1시간 간격) 기온·습도·강수량·하늘 상태
 * 초단기예보에는 강수확률이 없어서 rainProb 는 비워 둔다(null).
 * 같은 격자(nx, ny)와 같은 발표 시각의 결과는 기억해 두고 다시 쓰므로, 구가 달라도 격자가 같으면 한 번만 호출한다.
 */
@Component
public class KmaApiClient {

    private static final Logger log = LoggerFactory.getLogger(KmaApiClient.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final BigDecimal MISSING = new BigDecimal("-900");   // 기상청 결측값은 -999

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RestClient restClient;
    private final DbClock dbClock;
    private final String baseUrl;
    private final String serviceKey;
    private final Map<String, Object> memo = new ConcurrentHashMap<>();
    private boolean keyWarned = false;

    public KmaApiClient(DbClock dbClock,
                        @Value("${puf.kma.service-key:}") String serviceKey,
                        @Value("${puf.kma.base-url:https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}") String baseUrl,
                        @Value("${puf.kma.timeout-seconds:10}") int timeoutSeconds) {
        this.dbClock = dbClock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.serviceKey = normalizeKey(serviceKey);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    // Encoding 키(%2B 같은 퍼센트 표기)를 붙여 넣어도 쓸 수 있게 한 번 풀어 둔다. 주소를 만들 때 다시 인코딩한다
    private static String normalizeKey(String key) {
        String k = key == null ? "" : key.trim();
        return k.contains("%") ? URLDecoder.decode(k, StandardCharsets.UTF_8) : k;
    }

    // ------------------------------------------------------------------ 현재 날씨 (초단기실황)

    public KmaClient.Observation fetchObservation(WeatherArea area) {
        KmaGrid.Point grid = gridOf(area);
        if (grid == null || !hasKey()) {
            return null;
        }
        LocalDateTime now = dbClock.now();
        // 매시 정각 자료는 40분쯤 지나야 받을 수 있다 -> 40분 전을 정시로 내림. 아직 없으면 한 시간 전 것을 쓴다
        LocalDateTime base = now.minusMinutes(40).withMinute(0).withSecond(0).withNano(0);
        for (int back = 0; back < 2; back++) {
            LocalDateTime b = base.minusHours(back);
            String key = "N|" + grid.nx() + "," + grid.ny() + "|" + b.format(DATE_TIME);
            KmaClient.Observation cached = (KmaClient.Observation) memo.get(key);
            if (cached != null) {
                return cached;
            }
            JsonNode items = call("getUltraSrtNcst", grid, b.format(DATE), b.format(TIME));
            KmaClient.Observation o = items == null ? null : parseObservation(items, b);
            if (o != null) {
                remember(key, o);
                return o;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 앞으로의 예보 (초단기예보)

    @SuppressWarnings("unchecked")
    public List<KmaClient.ForecastPoint> fetchForecast(WeatherArea area) {
        KmaGrid.Point grid = gridOf(area);
        if (grid == null || !hasKey()) {
            return List.of();
        }
        LocalDateTime now = dbClock.now();
        // 초단기예보는 매시 30분에 발표되고 45분쯤부터 받을 수 있다 -> 45분 전 기준으로 가장 가까운 :30 발표를 쓴다
        LocalDateTime t = now.minusMinutes(45);
        LocalDateTime base = (t.getMinute() >= 30 ? t : t.minusHours(1)).withMinute(30).withSecond(0).withNano(0);
        for (int back = 0; back < 2; back++) {
            LocalDateTime b = base.minusHours(back);
            String key = "F|" + grid.nx() + "," + grid.ny() + "|" + b.format(DATE_TIME);
            List<KmaClient.ForecastPoint> cached = (List<KmaClient.ForecastPoint>) memo.get(key);
            if (cached != null) {
                return cached;
            }
            JsonNode items = call("getUltraSrtFcst", grid, b.format(DATE), b.format(TIME));
            List<KmaClient.ForecastPoint> points = items == null ? List.of() : parseForecast(items, now);
            if (!points.isEmpty()) {
                remember(key, points);
                return points;
            }
        }
        return List.of();
    }

    // ------------------------------------------------------------------ 호출

    private boolean hasKey() {
        if (serviceKey.isEmpty()) {
            if (!keyWarned) {
                keyWarned = true;
                log.warn("[kma] puf.kma.service-key 가 비어 있어 기상청을 호출하지 않습니다 (secret.kma.service-key 를 채워 주세요).");
            }
            return false;
        }
        return true;
    }

    private static KmaGrid.Point gridOf(WeatherArea area) {
        if (area.getLatitude() == null || area.getLongitude() == null) {
            return null;
        }
        return KmaGrid.of(area.getLatitude().doubleValue(), area.getLongitude().doubleValue());
    }

    private void remember(String key, Object value) {
        if (memo.size() > 500) {
            memo.clear();   // 키에 시각이 들어 있어 오래된 것은 어차피 안 쓰인다
        }
        memo.put(key, value);
    }

    /** 성공하면 items(item 배열), NODATA 이거나 실패하면 null */
    private JsonNode call(String operation, KmaGrid.Point grid, String baseDate, String baseTime) {
        // 인증키는 템플릿 변수로 넣고 encode() 로 엄격하게 인코딩한다 (키의 + / = 가 깨지지 않게)
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/" + operation)
                .queryParam("serviceKey", "{serviceKey}")
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 100)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", grid.nx())
                .queryParam("ny", grid.ny())
                .encode()
                .buildAndExpand(Map.of("serviceKey", serviceKey))
                .toUri();
        String text;
        try {
            text = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            log.warn("[kma] {} 호출 실패 (nx={}, ny={}): {}", operation, grid.nx(), grid.ny(), e.getMessage());
            return null;
        }
        JsonNode root;
        try {
            root = mapper.readTree(text == null ? "" : text);
        } catch (RuntimeException e) {
            // 키가 틀리거나 한도를 넘으면 JSON 이 아니라 XML 오류 문구가 오는 경우가 있다
            log.warn("[kma] {} 응답이 JSON 이 아닙니다: {}", operation, abbreviate(text));
            return null;
        }
        JsonNode header = root.path("response").path("header");
        String code = header.path("resultCode").asString("");
        if (!"00".equals(code)) {
            if ("03".equals(code)) {
                log.debug("[kma] {} 아직 자료 없음 (base {} {})", operation, baseDate, baseTime);
            } else {
                log.warn("[kma] {} 오류 resultCode={} msg={}", operation, code, header.path("resultMsg").asString(""));
            }
            return null;
        }
        JsonNode item = root.path("response").path("body").path("items").path("item");
        return item.isMissingNode() || item.isNull() ? null : item;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }

    // ------------------------------------------------------------------ 응답 해석 (단위 테스트가 직접 부른다)

    /** 초단기실황: T1H 기온(℃), REH 습도(%), RN1 1시간 강수량(mm). 셋 다 없으면 null */
    static KmaClient.Observation parseObservation(JsonNode items, LocalDateTime observedAt) {
        Map<String, String> values = new HashMap<>();
        for (JsonNode row : asList(items)) {
            values.put(row.path("category").asString(""), row.path("obsrValue").asString(""));
        }
        BigDecimal temp = number(values.get("T1H"), 1);
        BigDecimal humidityValue = number(values.get("REH"), 0);
        BigDecimal rain = rainMm(values.get("RN1"));
        if (temp == null && humidityValue == null && rain == null) {
            return null;
        }
        Integer humidity = humidityValue == null ? null : humidityValue.intValue();
        return new KmaClient.Observation(temp, humidity, rain, observedAt);
    }

    /** 초단기예보: 시각별로 T1H·REH·RN1·SKY·PTY 를 모아 한 시점씩 만든다. 이미 지난 시점은 뺀다 */
    static List<KmaClient.ForecastPoint> parseForecast(JsonNode items, LocalDateTime now) {
        Map<LocalDateTime, Map<String, String>> byTime = new TreeMap<>();
        for (JsonNode row : asList(items)) {
            String date = row.path("fcstDate").asString("");
            String time = row.path("fcstTime").asString("");
            if (date.isEmpty() || time.isEmpty()) {
                continue;
            }
            LocalDateTime target;
            try {
                target = LocalDateTime.parse(date + time, DATE_TIME);
            } catch (RuntimeException e) {
                continue;
            }
            byTime.computeIfAbsent(target, k -> new HashMap<>())
                    .put(row.path("category").asString(""), row.path("fcstValue").asString(""));
        }
        List<KmaClient.ForecastPoint> points = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Map<String, String>> e : byTime.entrySet()) {
            if (!e.getKey().isAfter(now.withMinute(0).withSecond(0).withNano(0))) {
                continue;
            }
            Map<String, String> v = e.getValue();
            BigDecimal humidity = number(v.get("REH"), 0);
            points.add(new KmaClient.ForecastPoint(e.getKey(), number(v.get("T1H"), 1),
                    humidity == null ? null : humidity.intValue(), rainMm(v.get("RN1")), null,
                    skyOf(v.get("SKY"), v.get("PTY"))));
        }
        return points;
    }

    private static Iterable<JsonNode> asList(JsonNode items) {
        if (items.isArray()) {
            return items;
        }
        return items.isObject() ? List.of(items) : List.of();
    }

    /** 숫자 문자열 -> BigDecimal. 비었거나 결측(-999)이면 null */
    static BigDecimal number(String text, int scale) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = NUMBER.matcher(text);
        if (!m.find()) {
            return null;
        }
        BigDecimal v = new BigDecimal(m.group());
        return v.compareTo(MISSING) < 0 ? null : v.setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * 강수량 문구 -> mm.  "강수없음" 0 / "1mm 미만" 0.5 / "1.0mm" 1.0 / "30.0~50.0mm" 40(가운데) / "50.0mm 이상" 50
     * 실황의 RN1 은 그냥 숫자("0", "1.5")로 온다
     */
    static BigDecimal rainMm(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        if (t.contains("없음")) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (t.contains("미만")) {
            return new BigDecimal("0.50");
        }
        List<BigDecimal> found = new ArrayList<>();
        Matcher m = NUMBER.matcher(t);
        while (m.find()) {
            found.add(new BigDecimal(m.group()));
        }
        if (found.isEmpty() || found.get(0).compareTo(MISSING) < 0) {
            return null;
        }
        BigDecimal v = found.size() >= 2 ? found.get(0).add(found.get(1)).divide(BigDecimal.valueOf(2)) : found.get(0);
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 강수 형태(PTY)가 있으면 그것을, 없으면 하늘 상태(SKY)를 한글로 */
    static String skyOf(String sky, String pty) {
        String precipitation = switch (pty == null ? "0" : pty.trim()) {
            case "1" -> "비";
            case "2" -> "비/눈";
            case "3" -> "눈";
            case "4" -> "소나기";
            case "5" -> "빗방울";
            case "6" -> "빗방울/눈날림";
            case "7" -> "눈날림";
            default -> null;
        };
        if (precipitation != null) {
            return precipitation;
        }
        return switch (sky == null ? "" : sky.trim()) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> null;
        };
    }
}
