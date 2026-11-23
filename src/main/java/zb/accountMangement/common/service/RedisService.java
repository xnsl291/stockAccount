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
  private static final RedisScript<Long> INCREMENT_WITH_EXPIRE = RedisScript.of(
      "local count = redis.call('incr', KEYS[1]) if count == 1 then redis.call('expire', KEYS[1], ARGV[1]) end return count", Long.class);

  private final StringRedisTemplate stringRedisTemplate;

  public String getData(String key){
    return stringRedisTemplate.opsForValue().get(key);
  }

  public void setData(String key, String value, Duration duration) {
    stringRedisTemplate.opsForValue().set(key, value, duration);
  }

  /**
   * 값을 1 늘림. 처음 만들 때만 만료 시간을 걸어서, 그 시간 안의 횟수를 세는 고정 구간이 됨.
   * 늘리기와 만료 설정을 Redis 안에서 한 번에 처리해, 중간에 끊겨도 만료 시간 없는 키가 남지 않음
   */
  public long increment(String key, Duration duration) {
    return stringRedisTemplate.execute(INCREMENT_WITH_EXPIRE, List.of(key), String.valueOf(duration.getSeconds()));
  }

  /**
   * 비교와 삭제를 Redis 안에서 한 번에 처리해 동시에 불러도 한 번만 성공함
   */
  public boolean deleteIfEquals(String key, String expected) {
    Long deleted = stringRedisTemplate.execute(DELETE_IF_EQUALS, List.of(key), expected);
    return deleted != null && deleted == 1;
  }

  public void deleteData(String key){
    stringRedisTemplate.delete(key);
  }
}
