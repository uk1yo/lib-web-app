package com.library.dao;

import com.library.model.BookCopy;

import java.util.List;
import java.util.Optional;

public interface BookCopyDao {
    BookCopy save(BookCopy bookCopy);

    Optional<BookCopy> findById(Long id);

    Optional<BookCopy> findByInventoryNumber(String inventoryNumber);

    List<BookCopy> findByBookId(Long bookId);

    void update(BookCopy bookCopy);

    void delete(Long id);
}
