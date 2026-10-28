package com.mbc.puf.common.pdf;

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
import com.itextpdf.layout.Style;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 통제정보 이관 PDF 생성기
 * - 경찰 이관: 차단/해제 정보
 * - 소방 이관: 잔류차량 목록
 * iText 9.x 기준 (setBold() 없음 → 폰트 사이즈/색상으로 강조)
 */
@Component
public class TransferPdfGenerator {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(0x1A, 0x56, 0xDB);
    private static final DeviceRgb ROW_ALT_BG = new DeviceRgb(0xF3, 0xF4, 0xF6);
    private static final DeviceRgb DARK = new DeviceRgb(0x11, 0x18, 0x27);
    private static final DeviceRgb LABEL_COLOR = new DeviceRgb(0x37, 0x40, 0x51);
    private static final DeviceRgb MUTED = new DeviceRgb(0x6B, 0x72, 0x80);
    private static final float[] COL_LABEL_CONTENT = {3f, 7f};
    private static final float MARGIN = 50f;

    /**
     * 도로 통제 정보 전달서 (통제 시작/해제 공용, A4 한 장).
     * 시설·구분·사유·통제 방식·담당자·해제(또는 통제 시작) 정보를 한 장으로 정리한다.
     */
    public byte[] generatePolicePdf(ControlNoticeData d) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(PdfDocument pdf=new PdfDocument(new PdfWriter(out));Document doc=new Document(pdf)) {
            doc.setMargins(50,45,65,45);PdfFont f=PdfVisuals.font();PdfVisuals.footer(pdf,f);
            letterHead(doc,f,"도로 통제 정보 전달서",d.blockEvent()?"통제 시작":"통제 해제");
            Table meta=PdfVisuals.table(1.5f,6);
            meta(meta,f,"시행일자",fmt(d.eventAt()));
            meta(meta,f,"수신","관할 경찰서장 · 관할 소방서장");
            meta(meta,f,"제목",orDash(d.underpassName())+(d.blockEvent()?" 통제 시작 알림":" 통제 해제 알림"));doc.add(meta);
            doc.add(PdfVisuals.p("아래와 같이 지하차도 통제 정보를 전달하오니 교통 관리 및 현장 대응 업무에 참고하여 주시기 바랍니다.",f,10).setMarginTop(18).setMarginBottom(20));
            Table t=PdfVisuals.table(112,393);
            formRow(t,f,"대상 시설",orDash(d.underpassName())+(d.address()==null?"":" / "+d.address()));
            formRow(t,f,d.blockEvent()?"통제 일시":"해제 일시",fmt(d.eventAt()));
            formRow(t,f,d.blockEvent()?"통제 사유":"해제 사유",orDash(d.reason()));
            if(d.reasonNote()!=null&&!d.reasonNote().isBlank())formRow(t,f,"상세 사유",d.reasonNote());
            String info="";
            if(d.riskScore()!=null)info=String.format(java.util.Locale.ROOT,"위험도 %.1f점",d.riskScore());
            if(d.rainfallMm()!=null)info+=(info.isBlank()?"":" / ")+String.format(java.util.Locale.ROOT,"강우량 %.1fmm",d.rainfallMm());
            formRow(t,f,"관측 정보",orDash(info));formRow(t,f,"통제 조치",methodLine(d));
            if(!d.blockEvent())formRow(t,f,"기존 통제",startInfoLine(d));
            formRow(t,f,"담당자",managerLine(d));
            if(d.memo()!=null&&!d.memo().isBlank())formRow(t,f,"비고",d.memo());
            doc.add(t);signature(doc,f);
        }catch(Exception e){throw new RuntimeException("통제 정보 전달서 PDF 생성 실패",e);}return out.toByteArray();
    }
    private void letterHead(Document d,PdfFont f,String title,String sub){
        d.add(PdfVisuals.p("PUF",f,18).setBorderBottom(new SolidBorder(PdfVisuals.INK,1)).setPaddingBottom(12).setMarginBottom(20));
        d.add(PdfVisuals.p(title,f,22).setTextAlignment(TextAlignment.CENTER).setMarginBottom(6));
        d.add(PdfVisuals.p(sub+" | PUF 지하차도 통합 관제",f,9).setFontColor(PdfVisuals.MUTED).setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));
    }
    private void meta(Table t,PdfFont f,String label,String value){
        t.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(6).add(PdfVisuals.p(label,f,10)));
        t.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(6).add(PdfVisuals.p(value,f,10)));
    }
    private void formRow(Table t,PdfFont f,String label,String value){
        t.addCell(PdfVisuals.cell(label,f,true).setPadding(9));t.addCell(PdfVisuals.cell(value,f,false).setPadding(9));
    }
    private void signature(Document d,PdfFont f){d.add(PdfVisuals.p("PUF 지하차도 통합 관제",f,13).setTextAlignment(TextAlignment.RIGHT).setMarginTop(20));}

    // 전달서 표의 한 행: 라벨 칸만 연회색, 내용 칸은 흰색
    private void addNoticeRow(Table table, PdfFont font, String label, String value) {
        table.addCell(labelCell(label, font, true));
        table.addCell(dataCell(value, font, false, TextAlignment.LEFT));
    }

    // ---- 전달서 문구 조립 ----

    private static final DateTimeFormatter NOTICE_DT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private String fmt(LocalDateTime t) {
        return t == null ? "-" : t.format(NOTICE_DT);
    }

    private String orDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    // "침수 위험 (위험도 76.4점 · 강우 18.0mm)" — 점수·강우는 있는 것만
    private String reasonLine(ControlNoticeData d) {
        StringBuilder sb = new StringBuilder(orDash(d.reason()));
        List<String> extra = new java.util.ArrayList<>();
        if (d.riskScore() != null) extra.add(String.format("위험도 %.1f점", d.riskScore()));
        if (d.rainfallMm() != null) extra.add(String.format("강우 %.1fmm", d.rainfallMm()));
        if (!extra.isEmpty()) sb.append(" (").append(String.join(" · ", extra)).append(")");
        return sb.toString();
    }

    // 입구 차단 · 전광판 "입구 통제 중 · 우회 바랍니다"
    private String methodLine(ControlNoticeData d) {
        String base = d.blockEvent() ? "입구 차단" : "입구 차단 해제";
        if (d.displayText() != null && !d.displayText().isBlank()) {
            return base + " · 전광판 \"" + d.displayText() + "\"";
        }
        return base;
    }

    // 홍길동 (부산 강서구 · 1조 06:00~14:00)
    private String managerLine(ControlNoticeData d) {
        List<String> info = new java.util.ArrayList<>();
        if (d.managerRegion() != null && !d.managerRegion().isBlank() && !"-".equals(d.managerRegion())) {
            info.add(d.managerRegion());
        }
        if (d.shiftLabel() != null && !d.shiftLabel().isBlank()) info.add(d.shiftLabel());
        String name = orDash(d.managerName());
        return info.isEmpty() ? name : name + " (" + String.join(" · ", info) + ")";
    }

    // 해제 문서: 통제 시작 일시·사유와 통제 시간
    private String startInfoLine(ControlNoticeData d) {
        StringBuilder sb = new StringBuilder(fmt(d.blockAt()));
        if (d.blockReason() != null && !d.blockReason().isBlank()) {
            sb.append(" · 사유: ").append(d.blockReason());
            if (d.blockReasonNote() != null && !d.blockReasonNote().isBlank()) {
                sb.append(" (").append(d.blockReasonNote()).append(")");
            }
        }
        if (d.blockAt() != null && d.eventAt() != null && !d.eventAt().isBefore(d.blockAt())) {
            long min = java.time.Duration.between(d.blockAt(), d.eventAt()).toMinutes();
            sb.append(" · 통제 시간 ").append(min).append("분");
        }
        return sb.toString();
    }

    /**
     * 소방 이관 PDF (잔류차량 목록)
     */
    public byte[] generateFirePdf(String underpassName,List<String> carNums,String managerName,String managerRegion,LocalDateTime transferAt,String memo) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(PdfDocument pdf=new PdfDocument(new PdfWriter(out));Document doc=new Document(pdf)) {
            doc.setMargins(50,45,65,45);PdfFont f=PdfVisuals.font();PdfVisuals.footer(pdf,f);
            letterHead(doc,f,"잔류차량 구조 요청서","잔류차량 이관");
            Table meta=PdfVisuals.table(1.5f,6);meta(meta,f,"시행일자",fmt(transferAt));meta(meta,f,"수신","관할 소방서장");meta(meta,f,"제목",orDash(underpassName)+" 잔류차량 구조 요청");doc.add(meta);
            doc.add(PdfVisuals.p("아래와 같이 지하차도 내 잔류차량 현황을 전달하오니 구조 업무에 참고하여 주시기 바랍니다.",f,10).setMarginTop(18).setMarginBottom(20));
            List<String> cars=carNums==null?List.of():carNums;
            Table info=PdfVisuals.table(112,393);formRow(info,f,"대상 시설",orDash(underpassName));formRow(info,f,"이관 일시",fmt(transferAt));formRow(info,f,"잔류차량","총 "+cars.size()+"대 / 차량번호 목록 아래 참조");formRow(info,f,"담당자",orDash(managerName)+" / "+orDash(managerRegion));
            if(memo!=null&&!memo.isBlank())formRow(info,f,"비고",memo);doc.add(info);
            PdfVisuals.section(doc,f,"붙임  잔류차량 번호 목록");Table list=PdfVisuals.table(1,5,3);
            for(String h:new String[]{"연번","차량번호","확인 사항"})list.addHeaderCell(PdfVisuals.cell(h,f,true));
            for(int i=0;i<cars.size();i++){list.addCell(PdfVisuals.cell(""+(i+1),f,false));list.addCell(PdfVisuals.cell(cars.get(i),f,false));list.addCell(PdfVisuals.cell("잔류 확인",f,false));}
            if(cars.isEmpty())list.addCell(new Cell(1,3).add(PdfVisuals.p("잔류차량 없음",f,10)));doc.add(list);signature(doc,f);
        }catch(Exception e){throw new RuntimeException("소방 이관 PDF 생성 실패",e);}return out.toByteArray();
    }

    // ---- 내부 헬퍼 ----

    // 프로젝트 내장 한글 폰트 (OS에 설치된 폰트에 의존하지 않도록 classpath 리소스로 번들)
    // Noto Sans CJK KR 에서 한글 음절/자모 + 라틴/기호만 추출한 서브셋 (약 1.9MB)
    private static final String FONT_RESOURCE_PATH = "fonts/NotoSansKR.ttf";

    private PdfFont loadFont() throws Exception {
        try (InputStream is = new ClassPathResource(FONT_RESOURCE_PATH).getInputStream()) {
            byte[] fontBytes = is.readAllBytes();
            return PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, EmbeddingStrategy.PREFER_EMBEDDED);
        } catch (Exception ignored) {
            // 내장 폰트 로드 실패 시 최후 수단 (한글은 깨지지만 PDF 생성 자체는 실패하지 않게)
            return PdfFontFactory.createFont(StandardFonts.HELVETICA);
        }
    }

    private void addTitle(Document doc, PdfFont font, String text) {
        doc.add(new Paragraph(text)
                .setFont(font).setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(DARK)
                .setMarginBottom(4f));
    }

    private void addSubtitle(Document doc, PdfFont font, String text) {
        doc.add(new Paragraph(text)
                .setFont(font).setFontSize(11)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(MUTED)
                .setMarginBottom(16f));
    }

    private Table twoColTable() {
        return new Table(UnitValue.createPercentArray(COL_LABEL_CONTENT))
                .setWidth(UnitValue.createPercentValue(100))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
    }

    private void addRow(Table table, PdfFont font, String label, String value, boolean alt) {
        table.addCell(labelCell(label, font, alt));
        table.addCell(dataCell(value, font, alt, TextAlignment.LEFT));
    }

    private Cell labelCell(String text, PdfFont font, boolean alt) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(10)
                        .setFontColor(LABEL_COLOR))
                .setBackgroundColor(alt ? ROW_ALT_BG : ColorConstants.WHITE)
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPaddingTop(8f).setPaddingBottom(8f).setPaddingLeft(12f);
    }

    private Cell dataCell(String text, PdfFont font, boolean alt, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(10)
                        .setTextAlignment(align)
                        .setFontColor(DARK))
                .setBackgroundColor(alt ? ROW_ALT_BG : ColorConstants.WHITE)
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPaddingTop(8f).setPaddingBottom(8f).setPaddingLeft(12f);
    }

    private Cell headerCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(10)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(LABEL_COLOR))
                .setBackgroundColor(ROW_ALT_BG)   // 정보 표 라벨 칸과 같은 연회색
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(8f);
    }

    private void addFooterNote(Document doc, PdfFont font) {
        doc.add(new Paragraph("\n").setFont(font).setFontSize(4));
        doc.add(new Paragraph("※ 본 문서는 PUF 지하차도 침수 예방 시스템에서 자동 생성된 이관 기록입니다.")
                .setFont(font).setFontSize(8)
                .setFontColor(new DeviceRgb(0x9C, 0xA3, 0xAF))
                .setTextAlignment(TextAlignment.CENTER));
    }
}