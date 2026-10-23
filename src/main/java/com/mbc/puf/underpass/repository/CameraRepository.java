package com.mbc.puf.underpass.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mbc.puf.underpass.entity.Camera;

public interface CameraRepository extends JpaRepository<Camera, Long> {

    // 특정 지하차도에 속한 카메라 전체 (삭제된 건 제외, 등록순)
    List<Camera> findByUnderpassIdAndDeletedFalseOrderByCreatedAtAscIdAsc(Long underpassId);

    // 카메라 단건 (삭제된 건 제외)
    Optional<Camera> findByIdAndDeletedFalse(Long id);
}
