package zb.accountMangement.member.model;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collection;
import java.util.Collections;

public enum RoleType {
  USER , WITHDRAWN, PENDING, ADMIN  ;

  public Collection<SimpleGrantedAuthority> getAuthorities() {
    return Collections.singletonList(new SimpleGrantedAuthority(this.name()));
  }
}
