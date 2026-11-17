package zb.accountMangement.common.service;


import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisService {
  private static final RedisScript<Long> DELETE_IF_EQUALS = RedisScript.of(
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

  private final StringRedisTemplate stringRedisTemplate;

  public String getData(String key){
    return stringRedisTemplate.opsForValue().get(key);
  }

  public void setData(String key, String value, Duration duration) {
    stringRedisTemplate.opsForValue().set(key, value, duration);
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
}
