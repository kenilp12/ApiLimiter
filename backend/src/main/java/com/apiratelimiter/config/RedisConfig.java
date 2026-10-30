package com.apiratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        return template;
    }

    @Bean(name = "fixedWindowScript")
    @SuppressWarnings("unchecked")
    public RedisScript<List<Long>> fixedWindowScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/fixed_window.lua"));
        script.setResultType((Class<List<Long>>) (Class<?>) List.class);
        return script;
    }

    @Bean(name = "slidingWindowScript")
    @SuppressWarnings("unchecked")
    public RedisScript<List<Long>> slidingWindowScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/sliding_window.lua"));
        script.setResultType((Class<List<Long>>) (Class<?>) List.class);
        return script;
    }

    @Bean(name = "tokenBucketScript")
    @SuppressWarnings("unchecked")
    public RedisScript<List<Long>> tokenBucketScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/token_bucket.lua"));
        script.setResultType((Class<List<Long>>) (Class<?>) List.class);
        return script;
    }

    @Bean(name = "leakyBucketScript")
    @SuppressWarnings("unchecked")
    public RedisScript<List<Long>> leakyBucketScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/leaky_bucket.lua"));
        script.setResultType((Class<List<Long>>) (Class<?>) List.class);
        return script;
    }
}
