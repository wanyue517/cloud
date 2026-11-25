package com.wanyue.cloud.common.mail;

public interface MailService {
    void sendText(String from, String to, String subject, String text);
}
