package zb.accountMangement.account;

import org.junit.jupiter.api.Test;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 묶인 금액은 0 이상, 잔액 이하로만 바뀌어야 함
 */
class AccountHeldAmountTest {

	private Account accountWith(long balance) {
		return Account.builder().balance(balance).build();
	}

	@Test
	void 잔액까지는_묶고_묶은_만큼_풀_수_있다() {
		Account account = accountWith(10_000);

		account.hold(10_000);
		assertThat(account.getHeldAmount()).isEqualTo(10_000);
		assertThat(account.getAvailableBalance()).isZero();

		account.releaseHold(10_000);
		assertThat(account.getHeldAmount()).isZero();
	}

	@Test
	void 쓸_수_있는_잔액보다_많이_묶으면_거절하고_묶인_금액은_그대로다() {
		Account account = accountWith(10_000);
		account.hold(6_000);

		assertThatThrownBy(() -> account.hold(4_001))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXCEED_BALANCE));
		assertThat(account.getHeldAmount()).isEqualTo(6_000);
	}

	@Test
	void 아주_큰_금액을_묶어도_long_넘침_없이_같은_오류로_거절한다() {
		Account account = accountWith(10_000);
		account.hold(1);

		assertThatThrownBy(() -> account.hold(Long.MAX_VALUE))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXCEED_BALANCE));
		assertThat(account.getHeldAmount()).isEqualTo(1);
	}

	@Test
	void 묶인_금액보다_많이_풀면_거절하고_묶인_금액은_그대로다() {
		Account account = accountWith(10_000);
		account.hold(3_000);

		assertThatThrownBy(() -> account.releaseHold(3_001))
				.isInstanceOf(IllegalStateException.class);
		assertThat(account.getHeldAmount()).isEqualTo(3_000);
	}

	@Test
	void 음수_금액으로_묶인_금액을_0_아래로_내릴_수_없다() {
		Account account = accountWith(10_000);

		assertThatThrownBy(() -> account.hold(-1))
				.isInstanceOf(IllegalStateException.class);
		assertThat(account.getHeldAmount()).isZero();
	}
}
