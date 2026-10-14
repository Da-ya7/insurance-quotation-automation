package com.insurance.quotation.config;

import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.insurance.quotation.mailbox.MailboxClient;
import com.insurance.quotation.mailbox.provider.DemoMailboxClient;
import com.insurance.quotation.mailbox.provider.GmailMailboxClient;
import com.insurance.quotation.service.ingestion.EmailIngestionService;

@Configuration
public class AutomationConfig {

    @Bean
    public GmailMailboxClient gmailMailboxClient() {
        return new GmailMailboxClient();
    }

    /** mailbox.mode=demo (default) or gmail */
    @Bean
    @Primary
    public MailboxClient mailboxClient(
            GmailMailboxClient gmail,
            @Value("${mailbox.mode:demo}") String mode) {

        return "gmail".equalsIgnoreCase(mode)
                ? gmail
                : new DemoMailboxClient();
    }

    @Bean
    public EmailIngestionService emailIngestionService(
            MailboxClient mailboxClient) {
        return new EmailIngestionService(mailboxClient);
    }
}
