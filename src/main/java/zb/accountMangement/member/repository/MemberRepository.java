package zb.accountMangement.member.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zb.accountMangement.member.model.entity.Member;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
  Optional<Member> findByPhoneNumber(String phoneNumber);

}
