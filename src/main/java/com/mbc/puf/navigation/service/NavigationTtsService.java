package com.mbc.puf.navigation.service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mbc.puf.common.exception.BusinessException;

/**
 * 내비 음성 안내(Gemini TTS). 프런트 개발서버(Node)의 /api/navigation/tts 와 같은 요청·응답 형식을 쓴다.
 * 무료 키는 분당 호출 수가 적어서, 만든 음성은 메모리에 두고 같은 목소리·문장이면 다시 부르지 않는다.
 */
@Service
public class NavigationTtsService {
    private static final Logger log = LoggerFactory.getLogger(NavigationTtsService.class);
    /** 프런트 navigation-voice.ts 의 aiVoices 와 이름을 맞춘다 */
    private static final Set<String> VOICES = Set.of("Kore", "Aoede", "Zephyr", "Sulafat", "Charon", "Iapetus", "Orus", "Puck");
    private static final Pattern RATE = Pattern.compile("rate=(\\d+)");
    private static final int CACHE_SIZE = 300;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String key, model;
    private final int limitPerMinute;
    /** 오래 안 쓴 음성부터 지운다 */
    private final Map<String, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    /** 미리 받기와 실제 안내가 같은 문장을 동시에 요청하면 한 번만 만든다 */
    private final Map<String, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>();
    /** 호출한 쪽(IP) -> {분 단위 시각, 그 분에 새로 만든 수} */
    private final Map<String, long[]> counters = new ConcurrentHashMap<>();

    public NavigationTtsService(@Value("${puf.assistant.gemini-key:}") String key,
                                @Value("${puf.navigation.tts-model:gemini-2.5-flash-preview-tts}") String model,
                                @Value("${puf.navigation.tts-limit-per-minute:20}") int limitPerMinute) {
        this.key = key == null ? "" : key.trim();
        this.model = model.trim();
        this.limitPerMinute = limitPerMinute;
    }

    public byte[] speech(String text, String voice, String clientKey) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || t.length() > 200 || voice == null || !VOICES.contains(voice)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "안내 문장과 목소리를 확인해 주세요.");
        }
        if (key.isEmpty() || !model.matches("^gemini-[a-zA-Z0-9._-]+$")) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "음성 생성 설정이 없습니다.");
        }
        String id = voice + "\n" + t;
        synchronized (cache) {
            byte[] stored = cache.get(id);
            if (stored != null) return stored;
        }
        checkLimit(clientKey);
        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> running = pending.putIfAbsent(id, mine);
        if (running == null) {
            try {
                byte[] data = generate(t, voice);
                synchronized (cache) { cache.put(id, data); }
                mine.complete(data);
            } catch (RuntimeException e) {
                mine.completeExceptionally(e);
            } finally {
                pending.remove(id);
            }
            running = mine;
        }
        try {
            return running.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException b) throw b;
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "음성을 만들지 못했습니다.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "음성을 만들지 못했습니다.");
        }
    }

    private byte[] generate(String text, String voice) {
        try {
            var root = mapper.createObjectNode();
            root.putArray("contents").addObject().putArray("parts").addObject().put("text", text);
            var config = root.putObject("generationConfig");
            config.putArray("responseModalities").add("AUDIO");
            config.putObject("speechConfig").putObject("voiceConfig").putObject("prebuiltVoiceConfig").put("voiceName", voice);
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json").header("x-goog-api-key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root))).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 429) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "음성 생성 사용량 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
            }
            if (res.statusCode() / 100 != 2) throw new IllegalStateException("Gemini TTS HTTP " + res.statusCode());
            // '우회전'처럼 너무 짧은 말은 음성 없이 돌아온다
            for (JsonNode p : mapper.readTree(res.body()).path("candidates").path(0).path("content").path("parts")) {
                String data = p.path("inlineData").path("data").asText("");
                if (data.isEmpty()) continue;
                Matcher m = RATE.matcher(p.path("inlineData").path("mimeType").asText(""));
                return wav(Base64.getDecoder().decode(data), m.find() ? Integer.parseInt(m.group(1)) : 24000);
            }
            throw new IllegalStateException("Gemini TTS 응답에 음성이 없음");
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "음성을 만들지 못했습니다.");
        } catch (Exception e) {
            log.warn("Gemini TTS 호출 실패: {}", e.toString());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "음성을 만들지 못했습니다.");
        }
    }

    /** Gemini 는 16bit 모노 PCM 을 주므로 브라우저가 바로 재생하도록 WAV 머리말을 붙인다 */
    private static byte[] wav(byte[] pcm, int rate) {
        ByteBuffer head = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        head.put("RIFF".getBytes()).putInt(36 + pcm.length).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(pcm.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        out.writeBytes(head.array());
        out.writeBytes(pcm);
        return out.toByteArray();
    }

    private void checkLimit(String clientKey) {
        long minute = System.currentTimeMillis() / 60_000;
        long[] c = counters.compute(clientKey == null ? "-" : clientKey, (k, v) -> {
            if (v == null || v[0] != minute) {
                return new long[]{minute, 1};
            }
            v[1]++;
            return v;
        });
        if (c[1] > limitPerMinute) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(e -> e.getValue()[0] != minute);   // 지난 분 기록 정리
        }
    }
}
