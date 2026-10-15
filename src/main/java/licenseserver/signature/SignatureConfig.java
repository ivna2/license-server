package licenseserver.signature;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(SignatureProperties.class)
public class SignatureConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
