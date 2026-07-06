package com.library.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TransactionAspect {

    private final ConnectionManager connectionManager;

    public TransactionAspect(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Around("@annotation(com.library.annotation.JdbcTransactional)")
    public Object manageTransaction(ProceedingJoinPoint pjp) throws Throwable {
        connectionManager.beginTransaction();
        try {
            Object result = pjp.proceed();
            connectionManager.commit();
            return result;
        } catch (Throwable t) {
            connectionManager.rollback();
            throw t;
        } finally {
            connectionManager.releaseConnection();
        }
    }
}
