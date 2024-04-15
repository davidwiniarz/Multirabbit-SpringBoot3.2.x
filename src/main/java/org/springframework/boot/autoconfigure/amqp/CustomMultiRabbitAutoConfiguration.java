package org.springframework.boot.autoconfigure.amqp;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.impl.CredentialsProvider;
import com.rabbitmq.client.impl.CredentialsRefreshService;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.MultiRabbitListenerConfigurationSelector;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnClass({RabbitTemplate.class, Channel.class})
@EnableConfigurationProperties({RabbitProperties.class, MultiRabbitProperties.class})
@Import({MultiRabbitListenerConfigurationSelector.class, RabbitAutoConfiguration.class})
public class CustomMultiRabbitAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomMultiRabbitAutoConfiguration.class);

    public CustomMultiRabbitAutoConfiguration() {
    }

    @Primary
    @Bean({"rabbitConnectionFactoryCreator"})
    @ConditionalOnProperty(
            prefix = "spring.multirabbitmq",
            name = {"enabled"},
            havingValue = "true"
    )
    RabbitAutoConfiguration.RabbitConnectionFactoryCreator rabbitConnectionFactoryCreator(RabbitProperties properties) {
        return new RabbitAutoConfiguration.RabbitConnectionFactoryCreator(properties);
    }

    @Configuration
    @DependsOn({"rabbitConnectionFactoryCreator"})
    @ConditionalOnProperty(
            prefix = "spring.multirabbitmq",
            name = {"enabled"},
            havingValue = "true"
    )
    protected static class MultiRabbitConnectionFactoryCreator implements BeanFactoryAware, ApplicationContextAware {
        private ConfigurableListableBeanFactory beanFactory;
        private ApplicationContext applicationContext;
        private final RabbitAutoConfiguration.RabbitConnectionFactoryCreator springFactoryCreator;

        MultiRabbitConnectionFactoryCreator(RabbitAutoConfiguration.RabbitConnectionFactoryCreator springFactoryCreator) {
            this.springFactoryCreator = springFactoryCreator;
        }


        @Bean
        @ConditionalOnMissingBean
        public ConnectionFactoryContextWrapper contextWrapper(ConnectionFactory connectionFactory) {
            return new ConnectionFactoryContextWrapper(connectionFactory);
        }

        @Bean
        @ConditionalOnMissingBean
        public MultiRabbitConnectionFactoryWrapper externalEmptyWrapper() {
            return new MultiRabbitConnectionFactoryWrapper();
        }

        @Bean
        public DefaultSslBundleRegistry sslBundles() {
            return new DefaultSslBundleRegistry();
        }

        @Primary
        @Bean({"multiRabbitConnectionFactory"})
        public ConnectionFactory routingConnectionFactory(RabbitProperties rabbitProperties, MultiRabbitProperties multiRabbitProperties, MultiRabbitConnectionFactoryWrapper externalWrapper, ResourceLoader resourceLoader, ObjectProvider<CredentialsProvider> credentialsProvider,
                                                          ObjectProvider<CredentialsRefreshService> credentialsRefreshService, ObjectProvider<ConnectionNameStrategy> connectionNameStrategy, ObjectProvider<ConnectionFactoryCustomizer> connectionFactoryCustomizers, ObjectProvider<SslBundles> sslBundles) throws Exception {
            MultiRabbitConnectionFactoryWrapper internalWrapper = this.instantiateConnectionFactories(rabbitProperties, multiRabbitProperties, resourceLoader, credentialsProvider, credentialsRefreshService, connectionNameStrategy, connectionFactoryCustomizers, sslBundles);
            MultiRabbitConnectionFactoryWrapper aggregatedWrapper = this.aggregateConnectionFactoryWrappers(internalWrapper, externalWrapper);
            if (aggregatedWrapper.getDefaultConnectionFactory() == null) {
                throw new IllegalArgumentException("A default ConnectionFactory must be provided.");
            } else {
                aggregatedWrapper.getEntries().forEach((name, value) -> {
                    this.registerContainerFactoryBean(name, value.getContainerFactory());
                    this.registerRabbitAdmins(name, value.getConnectionFactory());
                });
                SimpleRoutingConnectionFactory connectionFactory = new SimpleRoutingConnectionFactory();
                connectionFactory.setTargetConnectionFactories(aggregatedWrapper.getConnectionFactories());
                connectionFactory.setDefaultTargetConnectionFactory(aggregatedWrapper.getDefaultConnectionFactory());
                return connectionFactory;
            }
        }

        private MultiRabbitConnectionFactoryWrapper aggregateConnectionFactoryWrappers(MultiRabbitConnectionFactoryWrapper internalWrapper, MultiRabbitConnectionFactoryWrapper externalWrapper) {
            MultiRabbitConnectionFactoryWrapper aggregatedWrapper = new MultiRabbitConnectionFactoryWrapper();
            aggregatedWrapper.putEntriesFrom(internalWrapper);
            aggregatedWrapper.putEntriesFrom(externalWrapper);
            aggregatedWrapper.setDefaultConnectionFactory(externalWrapper.getDefaultConnectionFactory() != null ? externalWrapper.getDefaultConnectionFactory() : internalWrapper.getDefaultConnectionFactory());
            return aggregatedWrapper;
        }


        private MultiRabbitConnectionFactoryWrapper instantiateConnectionFactories(RabbitProperties rabbitProperties, MultiRabbitProperties multiRabbitProperties, ResourceLoader resourceLoader,
                                                                                   ObjectProvider<CredentialsProvider> credentialsProvider, ObjectProvider<CredentialsRefreshService> credentialsRefreshService,
                                                                                   ObjectProvider<ConnectionNameStrategy> connectionNameStrategy, ObjectProvider<ConnectionFactoryCustomizer> connectionFactoryCustomizer,
                                                                                   ObjectProvider<SslBundles> sslBundles
        ) throws Exception {
            MultiRabbitConnectionFactoryWrapper wrapper = new MultiRabbitConnectionFactoryWrapper();
            Map<String, RabbitProperties> propertiesMap = multiRabbitProperties != null ? multiRabbitProperties.getConnections() : Collections.emptyMap();
            Iterator<Map.Entry<String, RabbitProperties>> var10 = propertiesMap.entrySet().iterator();

            while (var10.hasNext()) {
                Map.Entry<String, RabbitProperties> entry = var10.next();
                var k = new PropertiesRabbitConnectionDetails(entry.getValue());
                RabbitConnectionFactoryBeanConfigurer rabbitConnectionFactoryBeanConfigurer = this.springFactoryCreator.rabbitConnectionFactoryBeanConfigurer(resourceLoader, k, credentialsProvider, credentialsRefreshService, sslBundles);
                CachingConnectionFactoryConfigurer rabbitCachingConnectionFactoryConfigurer = this.springFactoryCreator.rabbitConnectionFactoryConfigurer(k, connectionNameStrategy);
                CachingConnectionFactory connectionFactory = this.springFactoryCreator.rabbitConnectionFactory(rabbitConnectionFactoryBeanConfigurer, rabbitCachingConnectionFactoryConfigurer, connectionFactoryCustomizer);
                SimpleRabbitListenerContainerFactory containerFactory = this.newContainerFactory(connectionFactory);
                RabbitAdmin rabbitAdmin = this.newRabbitAdmin(connectionFactory);
                wrapper.addConnectionFactory((String) entry.getKey(), connectionFactory, containerFactory, rabbitAdmin);
            }

            String defaultConnectionFactoryKey = multiRabbitProperties != null ? multiRabbitProperties.getDefaultConnection() : null;
            if (StringUtils.hasText(defaultConnectionFactoryKey) && !multiRabbitProperties.getConnections().containsKey(defaultConnectionFactoryKey)) {
                String msg = String.format("MultiRabbitMQ broker '%s' set as default does not exist in configuration", defaultConnectionFactoryKey);
                CustomMultiRabbitAutoConfiguration.LOGGER.error(msg);
                throw new IllegalArgumentException(msg);
            } else {
                var k = new PropertiesRabbitConnectionDetails(multiRabbitProperties != null ? multiRabbitProperties.getConnections().get(defaultConnectionFactoryKey) : null);
                RabbitConnectionFactoryBeanConfigurer rabbitConnectionFactoryBeanConfigurer = this.springFactoryCreator.rabbitConnectionFactoryBeanConfigurer(resourceLoader, k, credentialsProvider, credentialsRefreshService, sslBundles);
                CachingConnectionFactoryConfigurer rabbitCachingConnectionFactoryConfigurer = this.springFactoryCreator.rabbitConnectionFactoryConfigurer(k, connectionNameStrategy);
                ConnectionFactory defaultConnectionFactory = StringUtils.hasText(defaultConnectionFactoryKey) ? (ConnectionFactory) wrapper.getConnectionFactories().get(defaultConnectionFactoryKey) : this.springFactoryCreator.rabbitConnectionFactory(rabbitConnectionFactoryBeanConfigurer, rabbitCachingConnectionFactoryConfigurer, connectionFactoryCustomizer);
                wrapper.setDefaultConnectionFactory(defaultConnectionFactory);
                return wrapper;
            }
        }

        private SimpleRabbitListenerContainerFactory newContainerFactory(ConnectionFactory connectionFactory) {
            SimpleRabbitListenerContainerFactory containerFactory = new SimpleRabbitListenerContainerFactory();
            containerFactory.setConnectionFactory(connectionFactory);
            return containerFactory;
        }

        private RabbitAdmin newRabbitAdmin(ConnectionFactory connectionFactory) {
            return new RabbitAdmin(connectionFactory);
        }

        private void registerContainerFactoryBean(String name, AbstractRabbitListenerContainerFactory<?> containerFactory) {
            this.beanFactory.registerSingleton(name, containerFactory);
        }

        private void registerRabbitAdmins(String name, ConnectionFactory connectionFactory) {
            String beanName = name + "-admin";
            RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
            rabbitAdmin.setApplicationContext(this.applicationContext);
            rabbitAdmin.setBeanName(beanName);
            rabbitAdmin.afterPropertiesSet();
            this.beanFactory.registerSingleton(beanName, rabbitAdmin);
        }

        public void setBeanFactory(BeanFactory beanFactory) {
            this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        }

        public void setApplicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }
    }
}
