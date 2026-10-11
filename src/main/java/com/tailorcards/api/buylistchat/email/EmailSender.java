package com.tailorcards.api.buylistchat.email;

/** Sends plain-text email. Implementations must not throw for delivery failures they can log. */
public interface EmailSender {

    void send(String to, String subject, String textBody);
}
