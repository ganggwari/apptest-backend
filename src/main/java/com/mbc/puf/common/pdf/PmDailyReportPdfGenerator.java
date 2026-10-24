package com.mbc.puf.common.pdf;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmEventStatus;
import com.mbc.puf.common.enums.PmLevel;
import com.mbc.puf.pm.dto.PmActionItem;
import com.mbc.puf.pm.dto.PmDailyReport;
import com.mbc.puf.pm.dto.PmEventItem;
import com.mbc.puf.pm.dto.PmLevelCounts;
import com.mbc.puf.pm.dto.PmPeriodSummary;
import com.mbc.puf.pm.dto.PmReportEquipment;
import com.mbc.puf.pm.dto.PmTypeCounts;

/**
 * 예지보전 데일리 리포트 PDF (관리자가 "PDF 뽑기" 버튼을 누르면 그 순간 만든다).
 * 폰트·색은 TransferPdfGenerator 와 같은 방식 (classpath 의 한글 폰트를 내장).
 */
@Component
public class PmDailyReportPdfGenerator {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(0x1A, 0x56, 0xDB);
    private static final DeviceRgb ROW_ALT_BG = new DeviceRgb(0xF3, 0xF4, 0xF6);
    private static final DeviceRgb DARK = new DeviceRgb(0x11, 0x18, 0x27);
    private static final DeviceRgb MUTED = new DeviceRgb(0x6B, 0x72, 0x80);
    private static final float MARGIN = 40f;
    private static final String FONT_RESOURCE_PATH = "fonts/NotoSansKR.ttf";

    public byte[] generate(PmDailyReport report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc = new Document(pdf)) {
            doc.setMargins(MARGIN, MARGIN, MARGIN, MARGIN);
            PdfFont font = loadFont();
            PmPeriodSummary s = report.summary();

            doc.add(new Paragraph("예지보전 데일리 리포트").setFont(font).setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER).setFontColor(DARK).setMarginBottom(4f));
            doc.add(new Paragraph(report.date().format(DATE_FMT) + "  (집계 " + fmt(s.from()) + " ~ " + fmt(s.to())
                    + ")  ·  생성 " + fmt(report.generatedAt()))
                    .setFont(font).setFontSize(9).setTextAlignment(TextAlignment.CENTER).setFontColor(MUTED)
                    .setMarginBottom(14f));

            // 1. 단계별 설비 수
            section(doc, font, "1. 설비 단계 현황 (전일 시작 시점 대비)");
            PmLevelCounts now = s.levelCounts();
            PmLevelCounts prev = s.previousLevelCounts();
            Table t1 = table(1, 1, 1, 1);
            for (String h : new String[]{"안전", "주의", "위험", "데이터 없음"}) {
                t1.addHeaderCell(header(h, font));
            }
            t1.addCell(cell(diff(now.safe(), prev == null ? null : prev.safe()), font, false, TextAlignment.CENTER));
            t1.addCell(cell(diff(now.caution(), prev == null ? null : prev.caution()), font, false, TextAlignment.CENTER));
            t1.addCell(cell(diff(now.danger(), prev == null ? null : prev.danger()), font, false, TextAlignment.CENTER));
            t1.addCell(cell(diff(now.noData(), prev == null ? null : prev.noData()), font, false, TextAlignment.CENTER));
            doc.add(t1);

            // 2. 설비 종류별
            section(doc, font, "2. 설비 종류별 현황");
            Table t2 = table(2, 1, 1, 1, 1, 1);
            for (String h : new String[]{"종류", "전체", "안전", "주의", "위험", "데이터 없음"}) {
                t2.addHeaderCell(header(h, font));
            }
            int i = 0;
            for (PmTypeCounts c : nz(s.byType())) {
                boolean alt = i++ % 2 == 1;
                t2.addCell(cell(typeName(c.type()), font, alt, TextAlignment.LEFT));
                t2.addCell(cell(String.valueOf(c.total()), font, alt, TextAlignment.CENTER));
                t2.addCell(cell(String.valueOf(c.safe()), font, alt, TextAlignment.CENTER));
                t2.addCell(cell(String.valueOf(c.caution()), font, alt, TextAlignment.CENTER));
                t2.addCell(cell(String.valueOf(c.danger()), font, alt, TextAlignment.CENTER));
                t2.addCell(cell(String.valueOf(c.noData()), font, alt, TextAlignment.CENTER));
            }
            doc.add(t2);

            // 3. 신규 점검 대기
            section(doc, font, "3. 오늘 새로 생긴 점검 대기 (" + s.newEventCount() + "건)");
            doc.add(eventTable(font, s.newEvents(), "신규 점검 대기가 없습니다."));

            // 4. 점검 기록
            section(doc, font, "4. 남긴 점검 기록 (" + s.actionCount() + "건)");
            Table t4 = table(2, 3, 2, 4, 1, 1);
            for (String h : new String[]{"시각", "설비 / 지하차도", "등록자", "조치 내용", "점검 전", "점검 후"}) {
                t4.addHeaderCell(header(h, font));
            }
            if (nz(s.actions()).isEmpty()) {
                t4.addCell(empty("남긴 점검 기록이 없습니다.", font, 6));
            }
            i = 0;
            for (PmActionItem a : nz(s.actions())) {
                boolean alt = i++ % 2 == 1;
                t4.addCell(cell(fmt(a.getCreatedAt()), font, alt, TextAlignment.LEFT));
                t4.addCell(cell(equipLabel(a.getName(), a.getType(), a.getEquipId()) + "\n" + dash(a.getUnderpassName()), font, alt, TextAlignment.LEFT));
                t4.addCell(cell(dash(a.getManagerName()), font, alt, TextAlignment.LEFT));
                t4.addCell(cell(dash(a.getContent()), font, alt, TextAlignment.LEFT));
                t4.addCell(cell(num(a.getScoreBefore()), font, alt, TextAlignment.CENTER));
                t4.addCell(cell(num(a.getScoreAfter()), font, alt, TextAlignment.CENTER));
            }
            doc.add(t4);

            // 5. 이월(처리 중)
            section(doc, font, "5. 아직 처리 중인 점검 (이월 " + s.openEventCount() + "건)");
            doc.add(eventTable(font, s.openEvents(), "처리 중인 점검이 없습니다."));

            // 6. 데이터 미수신
            section(doc, font, "6. 데이터 미수신 설비 (" + nz(s.stale()).size() + "건"
                    + (s.neverReceived() > 0 ? ", 한 번도 못 받은 설비 " + s.neverReceived() + "대 별도" : "") + ")");
            doc.add(equipTable(font, s.stale(), "데이터가 끊긴 설비가 없습니다."));

            // 7. 점검 권장
            section(doc, font, "7. 점검 권장 설비 (주의 이상, 점수 높은 순)");
            doc.add(equipTable(font, s.recommended(), "점검 권장 설비가 없습니다."));
        } catch (Exception e) {
            throw new RuntimeException("예지보전 데일리 리포트 PDF 생성 실패", e);
        }
        return baos.toByteArray();
    }

    private Table eventTable(PdfFont font, List<PmEventItem> events, String emptyText) {
        Table t = table(2, 3, 1, 1, 1, 2);
        for (String h : new String[]{"발생 시각", "설비 / 지하차도", "단계", "발생 점수", "현재 점수", "상태"}) {
            t.addHeaderCell(header(h, font));
        }
        if (nz(events).isEmpty()) {
            t.addCell(empty(emptyText, font, 6));
        }
        int i = 0;
        for (PmEventItem e : nz(events)) {
            boolean alt = i++ % 2 == 1;
            t.addCell(cell(fmt(e.getCreatedAt()), font, alt, TextAlignment.LEFT));
            t.addCell(cell(equipLabel(e.getName(), e.getType(), e.getEquipId()) + "\n" + dash(e.getUnderpassName()), font, alt, TextAlignment.LEFT));
            t.addCell(cell(levelName(e.getLevel()), font, alt, TextAlignment.CENTER));
            t.addCell(cell(num(e.getScore()), font, alt, TextAlignment.CENTER));
            t.addCell(cell(num(e.getCurrentScore()), font, alt, TextAlignment.CENTER));
            t.addCell(cell(statusName(e.getStatus()), font, alt, TextAlignment.CENTER));
        }
        return t;
    }

    private Table equipTable(PdfFont font, List<PmReportEquipment> list, String emptyText) {
        Table t = table(3, 3, 1, 1, 2, 2);
        for (String h : new String[]{"설비", "지하차도", "단계", "점수", "마지막 수신", "점검 상태"}) {
            t.addHeaderCell(header(h, font));
        }
        if (nz(list).isEmpty()) {
            t.addCell(empty(emptyText, font, 6));
        }
        int i = 0;
        for (PmReportEquipment q : nz(list)) {
            boolean alt = i++ % 2 == 1;
            t.addCell(cell(equipLabel(q.getName(), q.getType(), q.getEquipId()), font, alt, TextAlignment.LEFT));
            t.addCell(cell(dash(q.getUnderpassName()), font, alt, TextAlignment.LEFT));
            t.addCell(cell(levelName(q.getLevel()), font, alt, TextAlignment.CENTER));
            t.addCell(cell(num(q.getScore()), font, alt, TextAlignment.CENTER));
            t.addCell(cell(fmt(q.getLastReceivedAt()), font, alt, TextAlignment.LEFT));
            t.addCell(cell(q.getOpenEventStatus() == null ? "-" : statusName(q.getOpenEventStatus()), font, alt, TextAlignment.CENTER));
        }
        return t;
    }

    // ---- 헬퍼 ----

    private void section(Document doc, PdfFont font, String title) {
        doc.add(new Paragraph(title).setFont(font).setFontSize(12).setFontColor(DARK)
                .setMarginTop(14f).setMarginBottom(6f));
    }

    private Table table(float... widths) {
        return new Table(UnitValue.createPercentArray(widths)).setWidth(UnitValue.createPercentValue(100))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
    }

    private Cell header(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text).setFont(font).setFontSize(9).setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(HEADER_BG).setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)).setPadding(5f);
    }

    private Cell cell(String text, PdfFont font, boolean alt, TextAlignment align) {
        return new Cell().add(new Paragraph(text == null ? "-" : text).setFont(font).setFontSize(9).setTextAlignment(align))
                .setBackgroundColor(alt ? ROW_ALT_BG : ColorConstants.WHITE)
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)).setPadding(5f);
    }

    private Cell empty(String text, PdfFont font, int colspan) {
        return new Cell(1, colspan).add(new Paragraph(text).setFont(font).setFontSize(9)
                        .setTextAlignment(TextAlignment.CENTER).setFontColor(MUTED))
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)).setPadding(8f);
    }

    private PdfFont loadFont() throws Exception {
        try (InputStream is = new ClassPathResource(FONT_RESOURCE_PATH).getInputStream()) {
            return PdfFontFactory.createFont(is.readAllBytes(), PdfEncodings.IDENTITY_H, EmbeddingStrategy.PREFER_EMBEDDED);
        } catch (Exception ignored) {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA);
        }
    }

    private static <T> List<T> nz(List<T> l) {
        return l == null ? List.of() : l;
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "-" : t.format(DT_FMT);
    }

    private String dash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private String num(BigDecimal n) {
        return n == null ? "-" : n.stripTrailingZeros().toPlainString();
    }

    private String diff(int now, Integer before) {
        if (before == null) {
            return String.valueOf(now);
        }
        int d = now - before;
        return now + (d == 0 ? "  (변동 없음)" : d > 0 ? "  (+" + d + ")" : "  (" + d + ")");
    }

    /** 설비 이름(예: W-1-6). 이름이 없으면 종류 + 번호로 */
    private String equipLabel(String name, PmEquipmentType t, Long id) {
        return (name != null && !name.isBlank()) ? name : typeName(t) + " #" + id;
    }

    private String typeName(PmEquipmentType t) {
        if (t == null) {
            return "-";
        }
        return switch (t) {
            case CCTV -> "CCTV";
            case DISPLAY -> "전광판";
            case PUMP -> "배수펌프";
            case WATER_LEVEL -> "수위계";
        };
    }

    private String levelName(PmLevel l) {
        return l == null ? "-" : l.getDbValue();
    }

    private String statusName(PmEventStatus s) {
        if (s == null) {
            return "-";
        }
        return switch (s) {
            case CHECK_NEEDED -> "점검 필요";
            case IN_PROGRESS -> "점검 중";
            case RESOLVED -> "처리 완료";
            case ETC -> "기타";
        };
    }
}
