package zb.accountMangement.member.model;

public final class PhoneNumber {

	private PhoneNumber() {
	}

	/**
	 * 010-1234-5678, 010 1234 5678 같은 입력을 숫자만 남겨 한 가지 형식으로 맞춤
	 * @param phoneNumber - 핸드폰번호
	 * @return 숫자만 남은 핸드폰번호
	 */
	public static String normalize(String phoneNumber) {
		return phoneNumber.replaceAll("[^0-9]", "");
	}
}
