package dev.study.airag.adapter.out.cache.redis

import dev.study.airag.application.port.out.DocumentIndexingLease
import dev.study.airag.application.port.out.DocumentIndexingLockPort
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * 색인 이벤트별 Redis lease를 관리한다.
 *
 * Redis lease는 동시 실행을 줄일 뿐이며 처리 완료의 최종 판단은 PostgreSQL 기록이 담당한다.
 */
@Component
class RedisDocumentIndexingLockAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val meterRegistry: MeterRegistry,
    @Value("\${app.knowledge.consumer-name}") private val consumerName: String,
    @Value("\${app.knowledge.lock-ttl}") private val lockTtl: Duration,
) : DocumentIndexingLockPort {
    override fun tryAcquire(eventId: UUID): DocumentIndexingLease? {
        val lease = DocumentIndexingLease(eventId, UUID.randomUUID().toString())
        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey(eventId), lease.ownerToken, lockTtl) == true
        if (!acquired) {
            meterRegistry.counter("knowledge.indexing.lock.contention").increment()
            return null
        }
        return lease
    }

    override fun release(lease: DocumentIndexingLease) {
        redisTemplate.execute(RELEASE_SCRIPT, listOf(lockKey(lease.eventId)), lease.ownerToken)
    }

    private fun lockKey(eventId: UUID): String = "processing-lock:$consumerName:$eventId"

    companion object {
        private val RELEASE_SCRIPT =
            DefaultRedisScript(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long::class.java,
            )
    }
}
