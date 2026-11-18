package zb.accountMangement.common.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 저장 오류가 특정 유니크 제약 위반인지 판별함.
 * DataIntegrityViolationException에는 유니크 위반 말고도 길이 초과·NOT NULL 위반이 함께 담겨 오므로,
 * 중복 오류로 바꾸기 전에 어느 제약에 걸렸는지 확인해야 함
 */
public final class UniqueConstraint {

	private UniqueConstraint() {
	}

	/**
	 * @param e          - 저장 중 난 예외
	 * @param constraint - 유니크 제약 이름 (예: uk_member_phone_number)
	 * @return 그 제약을 위반해 난 예외이면 true
	 */
	// MySQL 8 + Hibernate 5에서는 제약 이름이 "member.uk_member_phone_number"처럼 테이블 이름을 앞에 붙여 옴.
	// 길이 초과는 DataException, NOT NULL 위반은 제약 이름이 null인 ConstraintViolationException으로 와서 걸러짐
	public static boolean isViolated(DataIntegrityViolationException e, String constraint) {
		if (!(e.getCause() instanceof ConstraintViolationException))
			return false;
		String name = ((ConstraintViolationException) e.getCause()).getConstraintName();
		return name != null && (name.equals(constraint) || name.endsWith("." + constraint));
	}
}
