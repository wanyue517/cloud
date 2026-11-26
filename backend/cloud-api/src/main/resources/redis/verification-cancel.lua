-- KEYS[1] 验证码 Key
-- ARGV[1] 本次签发 issueId

if redis.call('HGET', KEYS[1], 'issueId') ~= ARGV[1] then
    return 0
end

if redis.call('HGET', KEYS[1], 'state') ~= 'READY' then
    return 0
end

redis.call('DEL', KEYS[1])
return 1