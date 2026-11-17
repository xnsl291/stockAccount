package zb.accountMangement.common.service;


import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import zb.accountMangement.member.dto.SmsVerificationDto;

@Service
@RequiredArgsConstructor
public class RedisService {
  private static final RedisScript<Long> DELETE_IF_EQUALS = RedisScript.of(
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

  private final RedisTemplate<String, Object> redisTemplate;
  private final StringRedisTemplate stringRedisTemplate;

  public String getData(String key){
    ValueOperations<String,String> valueOperations = stringRedisTemplate.opsForValue();
    return valueOperations.get(key);
  }

  public void setData(String key, String value, Duration duration) {
    ValueOperations<String,String> valueOperations = stringRedisTemplate.opsForValue();
    valueOperations.set(key,value,duration);
  }

  /**
   * 저장된 값이 expected와 같을 때만 지움. 비교와 삭제를 Redis 안에서 한 번에 처리해 동시에 불러도 한 번만 성공함
   * @return 지웠으면 true
   */
  public boolean deleteIfEquals(String key, String expected) {
    Long deleted = stringRedisTemplate.execute(DELETE_IF_EQUALS, List.of(key), expected);
    return deleted != null && deleted == 1;
  }

  public void deleteData(String key){
    stringRedisTemplate.delete(key);
  }

  public void setMsgVerificationInfo(String key, String phone, String verificationCode,long duration) {
    SmsVerificationDto info = new SmsVerificationDto();
    info.setPhoneNumber(phone);
    info.setVerificationCode(verificationCode);

    HashOperations<String, String, SmsVerificationDto> valueOperations = redisTemplate.opsForHash();
    valueOperations.put(key, "info", info);

    redisTemplate.expire(key, duration, TimeUnit.MINUTES);
  }

  public SmsVerificationDto getMsgVerificationInfo(String key) {
    HashOperations<String, String, SmsVerificationDto> valueOperations = redisTemplate.opsForHash();
    return valueOperations.get(key, "info");
  }

  public void deleteMsgVerificationInfo(String key){
    redisTemplate.delete(key);
  }


}
