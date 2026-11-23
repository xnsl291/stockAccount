package zb.accountMangement.member.model;

public final class PhoneNumber {

	private PhoneNumber() {
	}

	public static String normalize(String phoneNumber) {
		return phoneNumber.replaceAll("[^0-9]", "");
	}
}
