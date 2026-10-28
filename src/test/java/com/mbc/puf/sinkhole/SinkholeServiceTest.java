package com.mbc.puf.sinkhole;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class SinkholeServiceTest {
    private SinkholeResultRequest request(boolean detected, int count) {
        return new SinkholeResultRequest("test-1","초량지하차도",null,OffsetDateTime.now(),detected,
                detected?new BigDecimal("0.87"):null,count,detected?12.3:null,"best.pt");
    }
    @Test void detectionRequiresEvidence() {
        assertThrows(RuntimeException.class,()->SinkholeService.validateEvidence(request(true,1),null));
    }
    @Test void acceptsJpegAndRejectsFakeImage() throws Exception {
        var bytes=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"jpg",bytes);
        assertDoesNotThrow(()->SinkholeService.validateEvidence(request(true,1),new MockMultipartFile("image","a.jpg","image/jpeg",bytes.toByteArray())));
        assertThrows(RuntimeException.class,()->SinkholeService.validateEvidence(request(true,1),new MockMultipartFile("image","a.jpg","image/jpeg","not a photo".getBytes())));
    }
    @Test void noDetectionCannotClaimBoxes() {
        assertDoesNotThrow(()->SinkholeService.validateEvidence(request(false,0),null));
        assertThrows(RuntimeException.class,()->SinkholeService.validateEvidence(request(false,4),null));
    }
    @Test void stateDistinguishesUnanalyzedAndFalsePositive() {
        assertEquals("미분석",SinkholeService.state(null));
        var r=new SinkholeService.Result(1,"job",1L,null,true,new BigDecimal("0.87"),10,12.0,
                java.time.LocalDateTime.now(),"best.pt","FALSE_POSITIVE","/image","ETC");
        assertEquals("오탐 확인",SinkholeService.state(r));
    }
}
