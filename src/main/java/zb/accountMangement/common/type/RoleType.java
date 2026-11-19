package zb.accountMangement.common.type;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collection;
import java.util.Collections;

public enum RoleType {
  USER , WITHDRAWN, PENDING, ADMIN  ;

  public Collection<SimpleGrantedAuthority> getAuthorities() {
    return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + this.name()));
  }
}
