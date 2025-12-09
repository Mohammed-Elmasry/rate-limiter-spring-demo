-- Fixed Window Rate Limiting Algorithm
-- KEYS[1]: The rate limit key prefix (e.g., "rl:fixed:user:123")
-- ARGV[1]: Maximum requests per window
-- ARGV[2]: Window size in seconds
-- ARGV[3]: Current timestamp in seconds
-- ARGV[4]: Request increment (typically 1)
--
-- Returns: {allowed (0/1), remaining requests, reset time in seconds}

local key_prefix = KEYS[1]
local max_requests = tonumber(ARGV[1])
local window_size = tonumber(ARGV[2])
local now_sec = tonumber(ARGV[3])
local increment = tonumber(ARGV[4])

-- Calculate current window
local window_id = math.floor(now_sec / window_size)
local window_key = key_prefix .. ":" .. window_id

-- Get current count for this window
local current_count = tonumber(redis.call('GET', window_key)) or 0

-- Check if request can be allowed
local allowed = 0
local remaining = max_requests - current_count
local reset_in_seconds = (window_id + 1) * window_size - now_sec

if current_count + increment <= max_requests then
    current_count = redis.call('INCRBY', window_key, increment)
    allowed = 1
    remaining = max_requests - current_count

    -- Set TTL to window size + buffer (ensures cleanup)
    redis.call('EXPIRE', window_key, window_size + 1)
end

return {allowed, remaining, reset_in_seconds}
