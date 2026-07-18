package com.measure.community.gateway.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DiscoveryHealthContributorContractTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CommonsClientAutoConfiguration.class,
                    HealthEndpointAutoConfiguration.class))
            .withBean(DiscoveryClient.class, () -> mock(DiscoveryClient.class));

    @Test
    void commonsDiscoveryHealthContributorIsRegisteredAsDiscoveryComposite() {
        contextRunner.run(context -> {
            HealthContributorRegistry registry = context.getBean(HealthContributorRegistry.class);

            assertThat(context).hasBean("discoveryCompositeHealthContributor");
            assertThat(registry.getContributor("discoveryComposite")).isNotNull();
        });
    }
}
