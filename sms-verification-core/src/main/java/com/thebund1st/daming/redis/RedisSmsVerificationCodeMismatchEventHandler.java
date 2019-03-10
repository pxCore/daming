package com.thebund1st.daming.redis;

import com.thebund1st.daming.core.MobilePhoneNumber;
import com.thebund1st.daming.core.SmsVerificationScope;
import com.thebund1st.daming.events.EventPublisher;
import com.thebund1st.daming.events.SmsVerificationCodeMismatchEvent;
import com.thebund1st.daming.events.SmsVerificationCodeVerifiedEvent;
import com.thebund1st.daming.events.TooManyFailureSmsVerificationAttemptsEvent;
import com.thebund1st.daming.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class RedisSmsVerificationCodeMismatchEventHandler {

    private final StringRedisTemplate redisTemplate;

    private final EventPublisher eventPublisher;

    private final Clock clock;

    @Setter
    private int threshold = 5;

    @EventListener
    public void on(SmsVerificationCodeMismatchEvent event) {
        String key = toKey(event.getMobile(), event.getScope());
        List<Object> attempts = redisTemplate.executePipelined((RedisCallback<Long>) connection -> {
            StringRedisConnection conn = (StringRedisConnection) connection;
            conn.sAdd(key, event.toString());
            conn.expireAt(key, event.getExpiresAt().toEpochSecond());
            conn.sCard(key);
            return null;
        });
        if (attempts.size() == 3) {
            if (toAttempts(attempts) >= threshold) {
                remove(key);
                eventPublisher.publish(new TooManyFailureSmsVerificationAttemptsEvent(UUID.randomUUID().toString(),
                        clock.now(),
                        event.getMobile(),
                        event.getScope()));
            }
        }
    }

    @EventListener
    public void on(SmsVerificationCodeVerifiedEvent event) {
        String key = toKey(event.getMobile(), event.getScope());
        remove(key);
    }

    private void remove(String key) {
        redisTemplate.delete(key);
    }

    private Long toAttempts(List<Object> attempts) {
        return (Long) attempts.get(attempts.size() - 1);
    }

    private String toKey(MobilePhoneNumber mobile, SmsVerificationScope scope) {
        return String.format("sms.verification.code.mismatch.%s.%s",
                mobile.getValue(), scope.getValue());
    }
}
