package com.library.dao;

import com.library.model.BorrowRecord;

import java.util.List;
import java.util.Optional;

public interface BorrowRecordDao {
    BorrowRecord save(BorrowRecord record);

    Optional<BorrowRecord> findById(Long id);

    List<BorrowRecord> findByUserId(Long userId);

    List<BorrowRecord> findByStatus(com.library.model.enums.BorrowStatus status, int offset, int limit);

    long countByStatus(com.library.model.enums.BorrowStatus status);

    void update(BorrowRecord record);
}
