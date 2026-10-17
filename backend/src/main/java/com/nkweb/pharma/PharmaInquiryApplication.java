package com.nkweb.pharma;

import com.nkweb.pharma.config.AppSecurityProperties;
import com.nkweb.pharma.config.AsyncProperties;
import com.nkweb.pharma.config.CorsProperties;
import com.nkweb.pharma.config.ExportProperties;
import com.nkweb.pharma.config.RateLimitProperties;
import com.nkweb.pharma.config.RequestLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the inquiry intake service.
 *
 * <p>The application is intentionally split into three independent stages so that a failure in
 * one stage can never destroy data produced by another:
 *
 * <ol>
 *   <li><b>Intake</b> – {@code POST /api/inquiries} validates, sanitises and commits the inquiry
 *       to PostgreSQL ({@code inquiries} table). The HTTP response is only produced after the
 *       commit succeeded, which makes acceptance of a submission durable.</li>
 *   <li><b>Export</b> – an asynchronous worker claims committed rows, appends them to the
 *       configured sink (Excel file or Google Sheets webhook) and marks them as exported.
 *       Claiming uses {@code SELECT ... FOR UPDATE SKIP LOCKED}, so several instances can run
 *       concurrently without ever exporting the same row twice.</li>
 *   <li><b>Recovery</b> – a scheduled sweeper re-queues rows that were interrupted (process
 *       crash, sink outage, locked file) which makes the pipeline self-healing.</li>
 * </ol>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({
        AppSecurityProperties.class,
        CorsProperties.class,
        RateLimitProperties.class,
        RequestLimitProperties.class,
        ExportProperties.class,
        AsyncProperties.class
})
public class PharmaInquiryApplication {

    public static void main(String[] args) {
        SpringApplication.run(PharmaInquiryApplication.class, args);
    }
}
