-- Sliding Window Log Rate Limiter using Redis Sorted Set (ZSET)
-- KEYS[1]: rate limit key, e.g. rl:sw:<identifier>
-- ARGV[1]: max limit
-- ARGV[2]: window duration in milliseconds
-- ARGV[3]: request cost
-- ARGV[4]: current timestamp in milliseconds
-- ARGV[5]: unique request identifier

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local cost = tonumber(ARGV[3]) or 1
local nowMs = tonumber(ARGV[4])
local requestId = ARGV[5] or tostring(nowMs)

-- Remove expired logs older than (nowMs - windowMs)
local clearBefore = nowMs - windowMs
redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)

local current = redis.call('ZCARD', key)

if current + cost <= limit then
    for i = 1, cost do
        redis.call('ZADD', key, nowMs, requestId .. ':' .. i)
    end
    local expireSec = math.ceil(windowMs / 1000) + 2
    redis.call('EXPIRE', key, expireSec)
    local remaining = limit - (current + cost)
    local resetSeconds = math.ceil(windowMs / 1000)
    return { 1, remaining, resetSeconds, 0 }
else
    -- Throttled: calculate when the oldest element will leave the window
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retryAfterSeconds = 1
    if #oldest >= 2 then
        local oldestScore = tonumber(oldest[2])
        local waitMs = (oldestScore + windowMs) - nowMs
        if waitMs > 0 then
            retryAfterSeconds = math.ceil(waitMs / 1000)
        end
    end
    local resetSeconds = retryAfterSeconds
    return { 0, 0, resetSeconds, retryAfterSeconds }
end
