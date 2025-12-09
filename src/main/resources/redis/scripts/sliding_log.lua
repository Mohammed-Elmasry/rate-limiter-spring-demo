-- Sliding Log Rate Limiting Algorithm
-- KEYS[1]: The rate limit key (e.g., "rl:sliding:user:123")
-- ARGV[1]: Maximum requests per window
-- ARGV[2]: Window size in milliseconds
-- ARGV[3]: Current timestamp in milliseconds
-- ARGV[4]: Request increment (typically 1)
-- ARGV[5]: TTL for the key in seconds
--
-- Returns: {allowed (0/1), remaining requests, reset time in seconds}

local key = KEYS[1]
local max_requests = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local increment = tonumber(ARGV[4])
local ttl = tonumber(ARGV[5])

-- Calculate window boundaries
local window_start = now - window_ms

-- Remove expired entries
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

-- Count current requests in window
local current_count = redis.call('ZCARD', key)

-- Check if request can be allowed
local allowed = 0
local remaining = max_requests - current_count

-- Calculate reset time (time until oldest entry expires)
local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
local reset_in_seconds = 0
if #oldest > 0 then
    local oldest_time = tonumber(oldest[2])
    reset_in_seconds = math.ceil((oldest_time + window_ms - now) / 1000)
    if reset_in_seconds < 0 then
        reset_in_seconds = 0
    end
end

if current_count + increment <= max_requests then
    -- Add new entries (support increment > 1)
    for i = 1, increment do
        -- Use timestamp + random suffix to ensure uniqueness
        local member = now .. ":" .. i .. ":" .. math.random(1000000)
        redis.call('ZADD', key, now, member)
    end
    allowed = 1
    remaining = max_requests - current_count - increment
else
    -- Calculate exact reset time when blocked
    if #oldest > 0 then
        local oldest_time = tonumber(oldest[2])
        reset_in_seconds = math.ceil((oldest_time + window_ms - now) / 1000)
    else
        reset_in_seconds = math.ceil(window_ms / 1000)
    end
end

-- Set TTL
redis.call('EXPIRE', key, ttl)

return {allowed, remaining, reset_in_seconds}
