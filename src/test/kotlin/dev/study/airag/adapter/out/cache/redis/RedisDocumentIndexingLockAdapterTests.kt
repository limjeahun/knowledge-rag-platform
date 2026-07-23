package dev.study.airag.adapter.out.cache.redis

import dev.study.airag.application.port.out.DocumentIndexingLease
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RedisDocumentIndexingLockAdapterTests {
    @Test
    fun `only the lease owner can release an event lock`() {
        val meterRegistry = SimpleMeterRegistry()
        val adapter =
            RedisDocumentIndexingLockAdapter(
                redisTemplate,
                meterRegistry,
                "indexer",
                Duration.ofMinutes(2),
            )
        val eventId = UUID.randomUUID()

        val owner = assertNotNull(adapter.tryAcquire(eventId))
        assertNull(adapter.tryAcquire(eventId))
        assertEquals(1.0, meterRegistry.counter("knowledge.indexing.lock.contention").count())

        adapter.release(DocumentIndexingLease(eventId, "different-owner"))
        assertNull(adapter.tryAcquire(eventId))

        adapter.release(owner)
        assertNotNull(adapter.tryAcquire(eventId))
    }

    companion object {
        private val redis = RedisContainer("redis:8.2.3-alpine").withExposedPorts(6379)
        private lateinit var connectionFactory: LettuceConnectionFactory
        private lateinit var redisTemplate: StringRedisTemplate

        @JvmStatic
        @BeforeAll
        fun startRedis() {
            redis.start()
            connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379))
            connectionFactory.afterPropertiesSet()
            connectionFactory.start()
            redisTemplate = StringRedisTemplate(connectionFactory).also { it.afterPropertiesSet() }
        }

        @JvmStatic
        @AfterAll
        fun stopRedis() {
            connectionFactory.stop()
            redis.stop()
        }
    }

    private class RedisContainer(
        imageName: String,
    ) : GenericContainer<RedisContainer>(imageName)
}
