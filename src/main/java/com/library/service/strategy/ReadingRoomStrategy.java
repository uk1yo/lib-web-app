package com.library.service.strategy;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class ReadingRoomStrategy implements DueDateStrategy {
    @Override
    public LocalDateTime calculateDueDate(LocalDateTime borrowedAt) {
        return borrowedAt.with(LocalTime.MAX);
    }
}
