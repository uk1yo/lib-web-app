package com.library.service.strategy;

import java.time.LocalDateTime;

public interface DueDateStrategy {
    LocalDateTime calculateDueDate(LocalDateTime borrowedAt);
}
