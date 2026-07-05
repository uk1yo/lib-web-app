package com.library.config;

import com.library.exception.DatabaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CustomConnectionPool}.
 *
 * <p>Uses Mockito to mock physical {@link Connection} objects — no real PostgreSQL
 * connection is required. The pool's internal queues are populated via reflection
 * (bypassing {@code @PostConstruct}) to make tests hermetic and fast.</p>
 *
 * <p>{@link MockitoSettings} is set to {@link Strictness#LENIENT} because
 * {@code setUp()} stubs {@code getAutoCommit()}, {@code isReadOnly()}, and
 * {@code isClosed()} on all mock connections; not every test exercises all three.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomConnectionPoolTest {

    private CustomConnectionPool pool;

    private static final int POOL_SIZE = 3;

    @Mock private Connection mock1;
    @Mock private Connection mock2;
    @Mock private Connection mock3;

    /**
     * Creates a pool without hitting the DB.
     * Injects @Value fields via reflection and pre-populates the queues with
     * ProxyConnections wrapping Mockito mocks.
     */
    @BeforeEach
    void setUp() throws SQLException {
        pool = new CustomConnectionPool();

        ReflectionTestUtils.setField(pool, "poolSize",  POOL_SIZE);
        ReflectionTestUtils.setField(pool, "timeoutMs", 1000L);
        ReflectionTestUtils.setField(pool, "url",      "jdbc:postgresql://localhost/test");
        ReflectionTestUtils.setField(pool, "username", "test");
        ReflectionTestUtils.setField(pool, "password", "test");
        ReflectionTestUtils.setField(pool, "driverClassName", "org.postgresql.Driver");

        // Stub clean initial state for all mocks
        when(mock1.getAutoCommit()).thenReturn(true);
        when(mock2.getAutoCommit()).thenReturn(true);
        when(mock3.getAutoCommit()).thenReturn(true);
        when(mock1.isReadOnly()).thenReturn(false);
        when(mock2.isReadOnly()).thenReturn(false);
        when(mock3.isReadOnly()).thenReturn(false);
        when(mock1.isClosed()).thenReturn(false);
        when(mock2.isClosed()).thenReturn(false);
        when(mock3.isClosed()).thenReturn(false);

        // Bypass @PostConstruct — build queues directly
        BlockingQueue<Connection> available = new ArrayBlockingQueue<>(POOL_SIZE);
        BlockingQueue<Connection> used      = new ArrayBlockingQueue<>(POOL_SIZE);
        available.offer(pool.new ProxyConnection(mock1));
        available.offer(pool.new ProxyConnection(mock2));
        available.offer(pool.new ProxyConnection(mock3));
        ReflectionTestUtils.setField(pool, "availableConnections", available);
        ReflectionTestUtils.setField(pool, "usedConnections",      used);
    }

    // =========================================================================
    // 1. Proxy Pattern — close() returns to pool, physical connection untouched
    // =========================================================================

    @Test
    @DisplayName("close() on ProxyConnection returns it to the pool — NOT physically closed")
    void proxyClose_returnsToPool_notPhysicallyClosed() throws SQLException {
        assertEquals(3, pool.getAvailableCount());
        assertEquals(0, pool.getUsedCount());

        Connection proxy = pool.getConnection();
        assertEquals(2, pool.getAvailableCount());
        assertEquals(1, pool.getUsedCount());

        proxy.close(); // ProxyConnection.close() — must return to pool

        assertEquals(3, pool.getAvailableCount());
        assertEquals(0, pool.getUsedCount());

        // Physical connections must NEVER be closed
        verify(mock1, never()).close();
        verify(mock2, never()).close();
        verify(mock3, never()).close();
    }

    // =========================================================================
    // 2. Used-queue tracking — usedConnections correctly maintained
    // =========================================================================

    @Test
    @DisplayName("usedConnections queue accurately tracks borrowed connections")
    void usedQueue_tracksActiveBorrows() throws SQLException {
        Connection c1 = pool.getConnection();
        Connection c2 = pool.getConnection();
        assertEquals(1, pool.getAvailableCount());
        assertEquals(2, pool.getUsedCount());

        c1.close();
        assertEquals(2, pool.getAvailableCount());
        assertEquals(1, pool.getUsedCount());

        c2.close();
        assertEquals(3, pool.getAvailableCount());
        assertEquals(0, pool.getUsedCount());
    }

    // =========================================================================
    // 3. State reset on close() — autoCommit and readOnly are reset
    // =========================================================================

    @Test
    @DisplayName("close() resets autoCommit=true on every connection that had an active transaction")
    void close_resetsAutoCommit() throws SQLException {
        // Simulate all connections returning autoCommit=false (pending transaction)
        when(mock1.getAutoCommit()).thenReturn(false);
        when(mock2.getAutoCommit()).thenReturn(false);
        when(mock3.getAutoCommit()).thenReturn(false);

        // Borrow ALL connections so we know exactly which mocks are involved
        Connection c1 = pool.getConnection();
        Connection c2 = pool.getConnection();
        Connection c3 = pool.getConnection();

        // Return all — each ProxyConnection.close() must call setAutoCommit(true) on its inner mock
        c1.close();
        c2.close();
        c3.close();

        // All three inner mocks must have been reset.
        // Note: setAutoCommit(true) is called TWICE per mock:
        //   1st time — in ProxyConnection constructor (initial clean state)
        //   2nd time — in ProxyConnection.close() (reset before returning to pool)
        verify(mock1, times(2)).setAutoCommit(true);
        verify(mock2, times(2)).setAutoCommit(true);
        verify(mock3, times(2)).setAutoCommit(true);
    }

    @Test
    @DisplayName("close() resets readOnly=false if it was set to true")
    void close_resetsReadOnly() throws SQLException {
        when(mock1.isReadOnly()).thenReturn(true); // simulate read-only mode

        Connection proxy = pool.getConnection();
        proxy.close();

        verify(mock1).setReadOnly(false); // must reset
    }

    // =========================================================================
    // 4. isClosed check — double-close throws SQLException
    // =========================================================================

    @Test
    @DisplayName("close() on an already-closed ProxyConnection throws SQLException")
    void close_onClosedConnection_throwsSQLException() throws SQLException {
        when(mock1.isClosed()).thenReturn(true); // simulate closed physical connection

        Connection proxy = pool.getConnection();

        assertThrows(SQLException.class, proxy::close,
                "Returning a closed connection should throw SQLException");
    }

    // =========================================================================
    // 5. Pool exhaustion — throws DatabaseException after timeout
    // =========================================================================

    @Test
    @DisplayName("getConnection() throws DatabaseException when pool is exhausted")
    void getConnection_poolExhausted_throwsDatabaseException() {
        List<Connection> held = new ArrayList<>();
        for (int i = 0; i < POOL_SIZE; i++) {
            held.add(pool.getConnection());
        }
        assertEquals(0, pool.getAvailableCount());

        assertThrows(DatabaseException.class, () -> pool.getConnection(),
                "Should throw DatabaseException after timeout when pool is exhausted");

        // Cleanup
        held.forEach(c -> {
            try { c.close(); } catch (Exception ignored) {}
        });
    }

    // =========================================================================
    // 6. Borrow/return cycles
    // =========================================================================

    @Test
    @DisplayName("Multiple sequential borrow/return cycles maintain correct counts")
    void borrowReturn_multipleCycles_maintainsCounts() throws SQLException {
        for (int i = 0; i < 7; i++) {
            Connection c = pool.getConnection();
            assertNotNull(c);
            c.close();
        }
        assertEquals(POOL_SIZE, pool.getAvailableCount());
        assertEquals(0, pool.getUsedCount());
    }

    // =========================================================================
    // 7. Concurrent access — threads receive distinct proxy instances
    // =========================================================================

    @Test
    @DisplayName("Concurrent threads each receive a distinct ProxyConnection")
    void concurrentBorrow_distinctProxiesPerThread() throws InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(POOL_SIZE);
        ConcurrentLinkedQueue<Connection> borrowed = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
        for (int i = 0; i < POOL_SIZE; i++) {
            executor.submit(() -> {
                try {
                    barrier.await(2, TimeUnit.SECONDS);
                    borrowed.add(pool.getConnection());
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(0, errors.get(), "No thread should fail to acquire a connection");
        assertEquals(POOL_SIZE, borrowed.size(), "Each thread must receive a connection");

        // All returned connections must be distinct instances
        List<Connection> list = new ArrayList<>(borrowed);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                assertNotSame(list.get(i), list.get(j),
                        "Two threads must not receive the same ProxyConnection instance");
            }
        }

        // Return all
        for (Connection c : borrowed) {
            try { c.close(); } catch (Exception ignored) {}
        }
        assertEquals(POOL_SIZE, pool.getAvailableCount());
        assertEquals(0, pool.getUsedCount());
    }

    // =========================================================================
    // 8. Pool metrics
    // =========================================================================

    @Test
    @DisplayName("getPoolSize(), getAvailableCount(), getUsedCount() report correct values")
    void poolMetrics_areAccurate() throws SQLException {
        assertEquals(POOL_SIZE, pool.getPoolSize());
        assertEquals(POOL_SIZE, pool.getAvailableCount());
        assertEquals(0,         pool.getUsedCount());

        Connection c = pool.getConnection();
        assertEquals(POOL_SIZE - 1, pool.getAvailableCount());
        assertEquals(1,             pool.getUsedCount());

        c.close();
        assertEquals(POOL_SIZE, pool.getAvailableCount());
        assertEquals(0,         pool.getUsedCount());
    }
}
