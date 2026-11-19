package zb.accountMangement.account.service;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Component;

/**
 * 14자리 숫자 계좌번호 생성. 같은 번호가 동시에 나오는 경우를 테스트에서 만들 수 있게 계좌 서비스와 떼어 빈으로 둠
 */
@Component
public class AccountNumberGenerator {
    private static final int ACCOUNT_NUMBER_LENGTH = 14;

    public String generate() {
        return RandomStringUtils.random(ACCOUNT_NUMBER_LENGTH, false, true);
    }
}
