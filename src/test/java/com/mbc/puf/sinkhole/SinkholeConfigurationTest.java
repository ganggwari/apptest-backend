package com.mbc.puf.sinkhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class SinkholeConfigurationTest {
    @Test void springLoadsTheConfiguredKoreanUnderpassNameWithoutMojibake() throws Exception {
        var sources=new PropertiesPropertySourceLoader().load("sinkhole-test",new ClassPathResource("application.properties"));
        var value=sources.get(0).getProperty("puf.sinkhole.underpass-name");
        assertEquals("${SINKHOLE_UNDERPASS_NAME:초량지하차도}",value);
    }
}
