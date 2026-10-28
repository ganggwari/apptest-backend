package com.mbc.puf.its.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.display.dto.VmsPageItem;
import com.mbc.puf.display.service.VmsDisplayService;
import com.mbc.puf.its.dto.ItsCctvItem;
import com.mbc.puf.its.dto.ItsVmsItem;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Service
public class ItsServiceImpl implements ItsService {

    private final EntityManager em;
    private final VmsDisplayService vmsDisplayService;

    private final double nearM;   // 이 거리 이하는 근접 전광판

    public ItsServiceImpl(EntityManager em, VmsDisplayService vmsDisplayService,
                          @Value("${puf.display.near-m:300}") double nearM) {
        this.em = em;
        this.vmsDisplayService = vmsDisplayService;
        this.nearM = nearM;
    }

    // ─── CCTV ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ItsCctvItem> getAllCctvs() {
        List<?> rows = em.createNativeQuery("SELECT id, its_id, name, lat, lng, stream_url FROM its_cctv ORDER BY id")
                .getResultList();
        List<ItsCctvItem> list = new ArrayList<>(rows.size());
        for (Object row : rows) {
            list.add(cctv((Object[]) row, null));
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItsCctvItem> getNearbyCctvs(Double lat, Double lng, Long underpassId, double radiusM, int limit) {
        double baseLat;
        double baseLng;
        if (underpassId != null) {
            List<?> up = em.createNativeQuery("SELECT lat, lng FROM underpass WHERE id = :id AND is_deleted = FALSE")
                    .setParameter("id", underpassId).getResultList();
            if (up.isEmpty()) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "지하차도를 찾을 수 없습니다. underpassId=" + underpassId);
            }
            Object[] r = (Object[]) up.get(0);
            baseLat = ((Number) r[0]).doubleValue();
            baseLng = ((Number) r[1]).doubleValue();
        } else if (lat != null && lng != null) {
            baseLat = lat;
            baseLng = lng;
        } else {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "lat/lng 또는 underpassId 중 하나는 필요합니다.");
        }

        // 위·경도 사각형으로 먼저 거르고(인덱스), 정확한 거리(하버사인)로 정렬한다
        double dLat = radiusM / 111_320.0;
        double dLng = radiusM / (111_320.0 * Math.max(0.01, Math.cos(Math.toRadians(baseLat))));
        String distExpr = "6371000 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(lat - :lat) / 2), 2)"
                + " + COS(RADIANS(:lat)) * COS(RADIANS(lat)) * POWER(SIN(RADIANS(lng - :lng) / 2), 2)))";
        Query q = em.createNativeQuery("SELECT id, its_id, name, lat, lng, stream_url, dist FROM ("
                + "SELECT id, its_id, name, lat, lng, stream_url, " + distExpr + " AS dist FROM its_cctv"
                + " WHERE lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng) t"
                + " WHERE dist <= :r ORDER BY dist, id");
        q.setParameter("lat", baseLat).setParameter("lng", baseLng)
                .setParameter("minLat", baseLat - dLat).setParameter("maxLat", baseLat + dLat)
                .setParameter("minLng", baseLng - dLng).setParameter("maxLng", baseLng + dLng)
                .setParameter("r", radiusM).setMaxResults(limit);
        List<?> rows = q.getResultList();
        List<ItsCctvItem> list = new ArrayList<>(rows.size());
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            list.add(cctv(r, (int) Math.round(((Number) r[6]).doubleValue())));
        }
        return list;
    }

    private static ItsCctvItem cctv(Object[] r, Integer distance) {
        return new ItsCctvItem(((Number) r[0]).longValue(), r[1] == null ? null : ((Number) r[1]).intValue(), (String) r[2],
                ((Number) r[3]).doubleValue(), ((Number) r[4]).doubleValue(), (String) r[5], distance);
    }

    // ─── 전광판 ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ItsVmsItem> getAllVms() {
        List<?> rows = em.createNativeQuery("SELECT id, its_no, spec, name, lat, lng, is_dummy FROM its_vms ORDER BY id")
                .getResultList();
        Map<Long, List<VmsPageItem>> pages = vmsDisplayService.pagesOf(null);
        List<ItsVmsItem> list = new ArrayList<>(rows.size());
        for (Object row : rows) {
            list.add(vms((Object[]) row, pages));
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public ItsVmsItem getVms(Long vmsId) {
        List<?> rows = em.createNativeQuery("SELECT id, its_no, spec, name, lat, lng, is_dummy FROM its_vms WHERE id = :id")
                .setParameter("id", vmsId).getResultList();
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "전광판을 찾을 수 없습니다. vmsId=" + vmsId);
        }
        return vms((Object[]) rows.get(0), vmsDisplayService.pagesOf(List.of(vmsId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItsVmsItem> getVmsByUnderpass(Long underpassId) {
        Number exists = (Number) em.createNativeQuery("SELECT COUNT(*) FROM underpass WHERE id = :id AND is_deleted = FALSE")
                .setParameter("id", underpassId).getSingleResult();
        if (exists.longValue() == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "지하차도를 찾을 수 없습니다. underpassId=" + underpassId);
        }
        List<?> rows = em.createNativeQuery("SELECT v.id, v.its_no, v.spec, v.name, v.lat, v.lng, v.is_dummy, vu.dist"
                + " FROM vms_underpass vu JOIN its_vms v ON v.id = vu.its_vms_id"
                + " WHERE vu.underpass_id = :id ORDER BY vu.dist, v.id").setParameter("id", underpassId).getResultList();
        List<Long> ids = new ArrayList<>(rows.size());
        for (Object row : rows) {
            ids.add(((Number) ((Object[]) row)[0]).longValue());
        }
        Map<Long, List<VmsPageItem>> pages = ids.isEmpty() ? Map.of() : vmsDisplayService.pagesOf(ids);
        List<ItsVmsItem> list = new ArrayList<>(rows.size());
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            int dist = ((Number) r[7]).intValue();
            list.add(vms(r, pages, dist, dist <= nearM ? "NEAR" : "FAR"));
        }
        return list;
    }

    private ItsVmsItem vms(Object[] r, Map<Long, List<VmsPageItem>> pages) {
        return vms(r, pages, null, null);
    }

    private ItsVmsItem vms(Object[] r, Map<Long, List<VmsPageItem>> pages, Integer dist, String zone) {
        Long id = ((Number) r[0]).longValue();
        List<VmsPageItem> list = pages.get(id);
        if (list == null || list.isEmpty()) {
            list = List.of(vmsDisplayService.defaultPage(id));
        }
        return new ItsVmsItem(id, r[1] == null ? null : ((Number) r[1]).intValue(), (String) r[2], (String) r[3],
                ((Number) r[4]).doubleValue(), ((Number) r[5]).doubleValue(), Boolean.TRUE.equals(r[6]), list, dist, zone);
    }
}
