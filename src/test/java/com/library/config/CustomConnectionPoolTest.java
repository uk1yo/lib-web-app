package com.library.config;

import com.library.exception.DatabaseException;
import org.junit.jupiter.api.AfterEach;
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
 * <p>These tests use Mockito to mock physical {@link Connection} objects so that
 * no actual PostgreSQL database is required. The tests verify the core pool
 * behaviours: proxy-based close, thread safety, and timeout handling.</p>
 *
 * <p>{@link MockitoSettings} is set to {@link Strictness#LENIENT} because
 * the {@code setUp()} method stubs {@code getAutoCommit()} on all three mock
 * connections, but not every test exercises all three connections.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomConnectionPoolTest {

    private CustomConnectionPool pool;

    /** Number of connections in the test pool. Kept small for speed. */
    private static final int TEST_POOL_SIZE = 3;

    @Mock
    private Connection mockConnection1;
    @Mock
    private Connection mockConnection2;
    @Mock
    private Connection mockConnection3;

    /**
     * Builds a {@link CustomConnectionPool} bypassing {@code @PostConstruct} (which
     * requires a real DB). Instead we inject mocks directly via reflection and
     * manually populate the internal queue.
     */
    @BeforeEach
    void setUp() throws SQLException {
        pool = new CustomConnectionPool();

        // Inject @Value fields via Spring's test utility
        ReflectionTestUtils.setField(pool, "poolSize", TEST_POOL_SIZE);
        ReflectionTestUtils.setField(pool, "timeoutMs", 1000L);
        ReflectionTestUtils.setField(pool, "url", "jdbc:postgresql://localhost/test");
        ReflectionTestUtils.setField(pool, "username", "test");
        ReflectionTestUtils.setField(pool, "password", "test");
        ReflectionTestUtils.setField(pool, "driverClassName", "org.postgresql.Driver");

        // Stub autoCommit so close() can reset it without NPE
        when(mockConnection1.getAutoCommit()).thenReturn(true);
        when(mockConnection2.getAutoCommit()).thenReturn(true);
        when(mockConnection3.getAutoCommit()).thenReturn(true);

        // Manually populate the pool's BlockingQueue with proxy-wrapped mocks
        // (simulates what @PostConstruct does without touching the DB)
        BlockingQueue<Connection> queue = new java.util.concurrent.ArrayBlockingQueue<>(TEST_POOL_SIZE);
        queue.offer(pool.new ProxyConnection(mockConnection1));
        queue.offer(pool.new ProxyConnection(mockConnection2));
        queue.offer(pool.new ProxyConnection(mockConnection3));
        ReflectionTestUtils.setField(pool, "pool", queue);
    }

    @AfterEach
    void tearDown() {
        // Nothing to tear down — mocked connections need no physical closing
    }

    // =========================================================================
    // Test 1: Proxy Pattern — close() returns to pool, not physically closed
    // =========================================================================

    @Test
    @DisplayName("close() on ProxyConnection returns it to the pool, NOT physically closed")
    void proxyClose_shouldReturnConnectionToPool_notClosePhysically() throws SQLException {
        // GIVEN: pool has 3 idle connections
        assertEquals(3, pool.getIdleConnectionCount(), "Pool should start with 3 idle connections");

        // WHEN: borrow a connection
        Connection proxy = pool.getConnection();
        assertEquals(2, pool.getIdleConnectionCount(), "After borrow: pool should have 2 idle");

        // WHEN: close the proxy (should return to pool)
        proxy.close();

        // THEN: pool should be full again
        assertEquals(3, pool.getIdleConnectionCount(), "After close: pool should return to 3 idle");

        // THEN: the underlying mock connection must NOT have been physically closed
        verify(mockConnection1, never()).close();
        verify(mockConnection2, never()).close();
        verify(mockConnection3, never()).close();
    }

    // =========================================================================
    // Test 2: Successive borrow and return cycles
    // =========================================================================

    @Test
    @DisplayName("Multiple borrow/return cycles maintain correct pool size")
    void borrowAndReturn_multipleCycles_maintainsPoolSize() throws SQLException {
        for (int i = 0; i < 5; i++) {
            Connection c = pool.getConnection();
            assertNotNull(c, "Borrowed connection must not be null, cycle=" + i);
            c.close(); // returns to pool
        }
        assertEquals(TEST_POOL_SIZE, pool.getIdleConnectionCount(),
                "Pool must be fully replenished after all borrow/return cycles");
    }

    // =========================================================================
    // Test 3: Pool exhaustion → DatabaseException after timeout
    // =========================================================================

    @Test
    @DisplayName("getConnection() throws DatabaseException when pool is exhausted")
    void getConnection_poolExhausted_throwsDatabaseException() throws InterruptedException {
        // GIVEN: borrow all connections without returning them
        List<Connection> held = new ArrayList<>();
        for (int i = 0; i < TEST_POOL_SIZE; i++) {
            held.add(pool.getConnection());
        }
        assertEquals(0, pool.getIdleConnectionCount(), "Pool should be fully exhausted");

        // WHEN / THEN: next borrow must throw DatabaseException (after timeout)
        assertThrows(DatabaseException.class, () -> pool.getConnection(),
                "Should throw DatabaseException when pool is exhausted and timeout expires");

        // Cleanup: return all connections so tearDown is clean
        for (Connection c : held) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    // =========================================================================
    // Test 4: Thread-safety — concurrent borrowers get distinct connections
    // =========================================================================

    @Test
    @DisplayName("Concurrent threads each receive distinct connections")
    void concurrentBorrow_threadsGetDistinctConnections() throws InterruptedException {
        int threadCount = TEST_POOL_SIZE;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ConcurrentLinkedQueue<Connection> borrowed = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await(2, TimeUnit.SECONDS); // synchronize start
                    Connection c = pool.getConnection();
                    borrowed.add(c);
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
                "All threads should finish within 5 seconds");

        assertEquals(0, errors.get(), "No thread should encounter errors");
        assertEquals(threadCount, borrowed.size(), "Each thread should have received a connection");

        // Verify all connections are distinct objects
        List<Connection> list = new ArrayList<>(borrowed);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                assertNotSame(list.get(i), list.get(j),
                        "Each thread must get a different ProxyConnection instance");
            }
        }

        // Return all connections to the pool
        for (Connection c : borrowed) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }

        assertEquals(TEST_POOL_SIZE, pool.getIdleConnectionCount(),
                "Pool must be fully replenished after all concurrent threads return connections");
    }

    // =========================================================================
    // Test 5: autoCommit reset on close()
    // =========================================================================

    @Test
    @DisplayName("Returning a connection resets autoCommit=true to prevent state leakage")
    void close_resetsAutoCommit_beforeReturningToPool() throws SQLException {
        // GIVEN: a connection that was used in a transaction (autoCommit=false)
        when(mockConnection1.getAutoCommit()).thenReturn(false);

        Connection proxy = pool.getConnection();

        // WHEN: close the proxy (simulating end of service method)
        proxy.close();

        // THEN: setAutoCommit(true) must have been called to reset state
        verify(mockConnection1).setAutoCommit(true);
    }

    // =========================================================================
    // Test 6: getPoolSize() and getIdleConnectionCount()
    // =========================================================================

    @Test
    @DisplayName("getPoolSize() returns configured capacity; getIdleConnectionCount() tracks idle connections")
    void poolMetrics_areAccurate() throws SQLException {
        assertEquals(TEST_POOL_SIZE, pool.getPoolSize(), "Pool capacity must equal configured size");
        assertEquals(3, pool.getIdleConnectionCount(), "Initially all connections should be idle");

        Connection c = pool.getConnection();
        assertEquals(2, pool.getIdleConnectionCount(), "After one borrow, 2 connections should be idle");

        c.close();
        assertEquals(3, pool.getIdleConnectionCount(), "After return, all 3 should be idle again");
    }
}
