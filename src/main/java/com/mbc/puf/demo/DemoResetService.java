package com.mbc.puf.demo;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.zaxxer.hikari.HikariDataSource;

/**
 * 시연 시작 상태로 DB 를 통째로 되돌린다 (Supabase 에 SQL 을 다시 미는 것과 같다). puf.demo.enabled=true 일 때만 존재한다.
 *
 * 순서: 1.스키마(전부 삭제 후 재생성) → 2.일반 시드 → 3.ITS 시드 → 4.예지보전 시드 → 5.시연 시작 상태 → 6.센서 한 줄씩
 * (classpath:demo/reset/1~6.sql). 5번이 "지금" 시각 기준이라 누를 때마다 시각이 새로 맞춰진다.
 * 끝나면 메모리에 있던 시연 상태(위험단계 고정, 설비 점수 고정, 호우 시나리오, 수위 기억)를 비우고 과거 날씨를 다시 채운다.
 */
@Service
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoResetService {

    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);
    private static final String[] FILES = { "1", "2", "3", "4", "5", "6" };

    /** 초기화 중에는 시연 스케줄러가 DB 를 건드리지 않게 한다 */
    private static final AtomicBoolean RESETTING = new AtomicBoolean(false);

    public static boolean isResetting() {
        return RESETTING.get();
    }

    private final DataSource dataSource;
    private final DemoScenarioState scenarioState;
    private final DemoLevelService levelService;
    private final DemoPmService pmService;
    private final DemoWaterProfile waterProfile;
    private final DemoStubScheduler stubScheduler;
    private final DemoWeatherHistoryRunner weatherRunner;

    public DemoResetService(DataSource dataSource, DemoScenarioState scenarioState, DemoLevelService levelService,
            DemoPmService pmService, DemoWaterProfile waterProfile, DemoStubScheduler stubScheduler,
            DemoWeatherHistoryRunner weatherRunner) {
        this.dataSource = dataSource;
        this.scenarioState = scenarioState;
        this.levelService = levelService;
        this.pmService = pmService;
        this.waterProfile = waterProfile;
        this.stubScheduler = stubScheduler;
        this.weatherRunner = weatherRunner;
    }

    public synchronized Map<String, Object> reset() {
        if (!RESETTING.compareAndSet(false, true)) {
            throw new IllegalStateException("이미 초기화 중입니다.");
        }
        long t0 = System.currentTimeMillis();
        try {
            // 진행 중인 스케줄러 tick 이 끝나길 잠깐 기다린다
            sleep(1500);
            if (scenarioState.current() != null) {
                scenarioState.end();
            }
            Map<String, Long> took = new LinkedHashMap<>();
            com.zaxxer.hikari.HikariPoolMXBean pool = dataSource instanceof HikariDataSource hd ? hd.getHikariPoolMXBean() : null;
            boolean suspended = false;
            try {
                // 앱의 다른 스레드가 테이블에 쓰는 도중에 스키마를 지우면 교착(deadlock)이 나므로, 풀을 멈추고 남은 연결을 끊고 시작한다
                if (pool != null) {
                    try {
                        pool.suspendPool();
                        suspended = true;
                        for (int i = 0; i < 40 && pool.getActiveConnections() > 0; i++) {
                            sleep(250);
                        }
                    } catch (RuntimeException e) {
                        log.warn("[DEMO-RESET] 커넥션 풀 일시정지 실패(allow-pool-suspension 확인): {}", e.toString());
                    }
                }
                try (Connection c = openDedicatedConnection()) {
                    c.setAutoCommit(true);
                    terminateOthers(c);
                    for (String f : FILES) {
                        long s = System.currentTimeMillis();
                        String sql = new String(new ClassPathResource("demo/reset/" + f + ".sql").getInputStream().readAllBytes(),
                                StandardCharsets.UTF_8);
                        runWithRetry(c, f, sql);
                        took.put(f + ".sql", System.currentTimeMillis() - s);
                        log.info("[DEMO-RESET] {}.sql 실행 완료 ({}ms)", f, System.currentTimeMillis() - s);
                    }
                }
            } finally {
                if (suspended) {
                    pool.resumePool();
                }
            }
            // 스키마가 새로 만들어졌으므로 기존 커넥션(타입·준비된 쿼리 캐시)을 버린다
            if (dataSource instanceof HikariDataSource h && h.getHikariPoolMXBean() != null) {
                h.getHikariPoolMXBean().softEvictConnections();
            }
            levelService.release(null);
            pmService.resetForReseed();
            waterProfile.clear();
            stubScheduler.forgetState();
            weatherRunner.run(null);
            long total = System.currentTimeMillis() - t0;
            log.info("[DEMO-RESET] 시연 시작 상태로 초기화 완료 ({}ms)", total);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("ok", true);
            res.put("elapsedMs", total);
            res.put("steps", took);
            return res;
        } catch (Exception e) {
            log.error("[DEMO-RESET] 초기화 실패", e);
            throw new IllegalStateException("초기화 실패: " + e.getMessage(), e);
        } finally {
            RESETTING.set(false);
        }
    }

    // 같은 DB·같은 계정의 다른 연결(풀에 남은 연결 등)을 끊는다. 풀은 멈춰 있으므로 다시 붙으며 쓰지 않는다
    private void terminateOthers(Connection c) {
        try (Statement st = c.createStatement()) {
            st.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND usename = current_user AND pid <> pg_backend_pid()");
        } catch (SQLException e) {
            log.warn("[DEMO-RESET] 다른 연결 끊기 실패(계속 진행): {}", e.getMessage());
        }
    }

    // 교착(40P01)·직렬화 실패(40001) 면 롤백 후 최대 4번 다시 시도한다
    private void runWithRetry(Connection c, String name, String sql) throws SQLException {
        for (int attempt = 1; ; attempt++) {
            try (Statement st = c.createStatement()) {
                st.execute(sql);
                return;
            } catch (SQLException e) {
                boolean retry = ("40P01".equals(e.getSQLState()) || "40001".equals(e.getSQLState())) && attempt < 4;
                try (Statement st = c.createStatement()) {
                    st.execute("ROLLBACK");
                } catch (SQLException ignore) {
                    // 열린 트랜잭션이 없으면 경고만 나온다
                }
                if (!retry) {
                    throw e;
                }
                log.warn("[DEMO-RESET] {}.sql 교착 → {}번째 재시도", name, attempt);
                terminateOthers(c);
                sleep(1000);
            }
        }
    }

    // 커넥션 풀과 따로 한 줄 연결을 새로 연다 (스키마를 지우는 도중 풀 커넥션을 쓰지 않으려고)
    private Connection openDedicatedConnection() throws SQLException {
        HikariDataSource h = dataSource instanceof HikariDataSource hd ? hd : dataSource.unwrap(HikariDataSource.class);
        Connection c = DriverManager.getConnection(h.getJdbcUrl(), h.getUsername(), h.getPassword());
        try (Statement st = c.createStatement()) {
            st.execute("SET TIME ZONE 'Asia/Seoul'");
        }
        return c;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
