-- Token Bucket Rate Limiting Algorithm
-- KEYS[1]: The rate limit key (e.g., "rl:token:user:123")
-- ARGV[1]: Bucket capacity (max tokens)
-- ARGV[2]: Refill rate (tokens per second)
-- ARGV[3]: Current timestamp in milliseconds
-- ARGV[4]: Requested tokens (typically 1)
-- ARGV[5]: TTL for the key in seconds
--
-- Returns: {allowed (0/1), remaining tokens, reset time in seconds}

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])
local ttl = tonumber(ARGV[5])

-- Get current state
local state = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(state[1])
local last_refill = tonumber(state[2])

-- Initialize if key doesn't exist
if tokens == nil then
    tokens = capacity
    last_refill = now
end

-- Calculate token refill based on elapsed time
local elapsed_ms = math.max(0, now - last_refill)
local elapsed_sec = elapsed_ms / 1000.0
local tokens_to_add = refill_rate * elapsed_sec
tokens = math.min(capacity, tokens + tokens_to_add)

-- Check if request can be allowed
local allowed = 0
local remaining = math.floor(tokens)
local reset_in_seconds = 0

if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
    remaining = math.floor(tokens)
else
    -- Calculate time until enough tokens are available
    local tokens_needed = requested - tokens
    reset_in_seconds = math.ceil(tokens_needed / refill_rate)
end

-- Update state
redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)
redis.call('EXPIRE', key, ttl)

return {allowed, remaining, reset_in_seconds}
