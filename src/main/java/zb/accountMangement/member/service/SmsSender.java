package zb.accountMangement.member.service;

public interface SmsSender {

	void send(String phoneNumber, String text);
}
