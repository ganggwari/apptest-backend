package com.mbc.puf.region.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.member.repository.ManagerRegionRepository;
import com.mbc.puf.region.dto.RegionCreateRequest;
import com.mbc.puf.region.dto.RegionResponse;
import com.mbc.puf.region.dto.RegionUpdateRequest;
import com.mbc.puf.region.entity.Region;
import com.mbc.puf.region.repository.RegionRepository;
import com.mbc.puf.underpass.repository.UnderpassRepository;

@Service
public class RegionServiceImpl implements RegionService {

    // 지역 삭제 시 소속 데이터를 옮겨두는 특수 지역 (수정/삭제 금지)
    public static final String UNASSIGNED_REGION_NAME = "미배정";

    private final RegionRepository regionRepository;
    private final UnderpassRepository underpassRepository;
    private final ManagerRegionRepository managerRegionRepository;

    public RegionServiceImpl(RegionRepository regionRepository, UnderpassRepository underpassRepository,
                             ManagerRegionRepository managerRegionRepository) {
        this.regionRepository = regionRepository;
        this.underpassRepository = underpassRepository;
        this.managerRegionRepository = managerRegionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionResponse> getRegionList() {
        return regionRepository.findByDeletedFalseOrderByIdAsc().stream().map(RegionServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public RegionResponse createRegion(RegionCreateRequest request) {
        String name = request.getName().trim();

        if (regionRepository.findByNameAndDeletedFalse(name).isPresent()) {
            throw duplicateName(name);
        }

        Region region = new Region();
        region.setName(name);
        try {
            regionRepository.saveAndFlush(region);   // flush 해야 유니크 위반이 여기서 바로 터진다
        } catch (DataIntegrityViolationException e) {   // 위 확인과 insert 사이에 동시 요청이 들어온 경우
            throw duplicateName(name);
        }
        return toResponse(region);
    }

    @Override
    @Transactional
    public RegionResponse updateRegion(Long id, RegionUpdateRequest request) {
        Region region = getActiveRegion(id);
        blockIfUnassigned(region, "'미배정' 지역은 이름을 변경할 수 없습니다.");

        String name = request.getName().trim();

        regionRepository.findByNameAndDeletedFalse(name)
                .filter(sameName -> !sameName.getId().equals(id))   // 자기 자신과 같은 이름은 허용
                .ifPresent(sameName -> { throw duplicateName(name); });

        region.setName(name);
        try {
            regionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw duplicateName(name);
        }
        return toResponse(region);
    }

    @Override
    @Transactional
    public void deleteRegion(Long id) {
        Region region = getActiveRegion(id);
        blockIfUnassigned(region, "'미배정' 지역은 삭제할 수 없습니다.");

        Region unassigned = regionRepository.findByNameAndDeletedFalse(UNASSIGNED_REGION_NAME)
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "'미배정' 지역이 존재하지 않습니다. 시드 데이터를 확인해 주세요."));

        // 네 작업은 한 트랜잭션: 중간에 실패하면 전부 롤백된다.
        // 소프트 삭제를 먼저 표시해야 한다: 아래 일괄 UPDATE가 실행 전에 이 변경을 DB에 반영(flush)한 뒤
        // 영속성 컨텍스트를 비우기 때문에, 순서를 바꾸면 region 변경이 저장되지 않고 사라진다
        region.setDeleted(true);                                          // 소프트 삭제
        underpassRepository.moveRegion(id, unassigned.getId());          // 소속 지하차도 이동(삭제된 지하차도 제외)
        // 이 지역 담당자들은 '미배정'을 담당하게 옮긴다(이미 '미배정'을 가진 관리자는 그대로) → 원래 지역 행은 지움
        managerRegionRepository.copyRegion(id, unassigned.getId());
        managerRegionRepository.deleteByRegionId(id);
    }

    // ---- 내부 메서드 ----

    // 삭제되지 않은 지역 조회, 없으면 404
    private Region getActiveRegion(Long id) {
        return regionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 지역입니다. id=" + id));
    }

    // '미배정' 이면 400
    private void blockIfUnassigned(Region region, String message) {
        if (UNASSIGNED_REGION_NAME.equals(region.getName())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private BusinessException duplicateName(String name) {
        return new BusinessException(HttpStatus.CONFLICT, "이미 존재하는 지역 이름입니다. name=" + name);
    }

    private static RegionResponse toResponse(Region region) {
        RegionResponse response = new RegionResponse();
        response.setId(region.getId());
        response.setName(region.getName());
        return response;
    }
}
