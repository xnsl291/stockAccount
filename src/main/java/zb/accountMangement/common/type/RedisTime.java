package zb.accountMangement.common.type;

import lombok.Getter;

@Getter
public enum RedisTime {
  PHONE_VALID(2), PHONE_VERIFIED(10);

  private long time;

  private RedisTime(long time) {
    this.time = time;
  }
}
