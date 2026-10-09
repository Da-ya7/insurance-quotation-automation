package com.insurance.quotation.config;

import com.insurance.quotation.mailbox.MailboxClient;
import com.insurance.quotation.mailbox.provider.DemoMailboxClient;
import com.insurance.quotation.service.ingestion.EmailIngestionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AutomationConfig {

    @Bean
    public MailboxClient demoMailboxClient() {
        return new DemoMailboxClient();
    }

    @Bean
    public EmailIngestionService emailIngestionService(
            MailboxClient mailboxClient) {

        return new EmailIngestionService(mailboxClient);
    }
}