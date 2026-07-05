package com.library.service.strategy;

import java.time.LocalDateTime;

public class HomeLendingStrategy implements DueDateStrategy {
    @Override
    public LocalDateTime calculateDueDate(LocalDateTime borrowedAt) {
        return borrowedAt.plusDays(14);
    }
}
