package com.mbc.puf.demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * [시연용·Render] 시작 상태 시드(5.seed_demo SQL)의 싱크홀 탐지 건(pm_road_result.image_name = sinkhole-demo.jpg)에 쓸 사진을
 * 싱크홀 사진 폴더(puf.sinkhole.image-dir)에 복사해 둔다. Render 는 서버를 다시 켜면 파일이 사라지므로 켤 때마다 확인해서 없으면 복사한다.
 * 사진은 jar 안(resources/demo/sinkhole-demo.jpg)에 들어 있다.
 */
@Component
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoSinkholeImageInstaller implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSinkholeImageInstaller.class);
    static final String IMAGE_NAME = "sinkhole-demo.jpg";

    private final Path imageDir;

    public DemoSinkholeImageInstaller(@Value("${puf.sinkhole.image-dir:./sinkhole-images}") String imageDir) {
        this.imageDir = Path.of(imageDir).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Files.createDirectories(imageDir);
            Path target = imageDir.resolve(IMAGE_NAME);
            if (Files.isRegularFile(target)) {
                return;
            }
            try (InputStream in = new ClassPathResource("demo/" + IMAGE_NAME).getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[DEMO] 싱크홀 시연 사진 복사: {}", target);
        } catch (IOException | RuntimeException e) {
            log.warn("[DEMO] 싱크홀 시연 사진 복사 실패 (탐지 사진이 안 보일 수 있음): {}", e.toString());
        }
    }
}
