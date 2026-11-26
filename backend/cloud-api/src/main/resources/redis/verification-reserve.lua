-- KEYS[1] 验证码 Key
-- ARGV[1] 用户提交验证码的 HMAC
-- ARGV[2] 本次注册请求 owner
-- ARGV[3] 最大错误次数

local key = KEYS[1]

if redis.call('EXISTS', key) == 0 then
    return -1
end

if redis.call('HGET', key, 'state') ~= 'READY' then
    return -2
end

if redis.call('HGET', key, 'hash') ~= ARGV[1] then
    local count = redis.call('HINCRBY', key, 'failures', 1)

    if count >= tonumber(ARGV[3]) then
        redis.call('DEL', key)
        return -4
    end

    return -3
end

redis.call('HSET', key,
    'state', 'RESERVED',
    'owner', ARGV[2]
)

return 1