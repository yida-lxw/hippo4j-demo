package cn.hippo4j.springboot.starter.config;

import cn.hippo4j.adapter.base.ThreadPoolAdapterBeanContainer;
import cn.hippo4j.common.api.ThreadDetailState;
import cn.hippo4j.common.api.ThreadPoolCheckAlarm;
import cn.hippo4j.common.api.ThreadPoolConfigChange;
import cn.hippo4j.common.api.ThreadPoolDynamicRefresh;
import cn.hippo4j.common.config.ApplicationContextHolder;
import cn.hippo4j.core.config.UtilAutoConfiguration;
import cn.hippo4j.core.enable.MarkerConfiguration;
import cn.hippo4j.core.executor.support.service.DynamicThreadPoolService;
import cn.hippo4j.core.handler.DynamicThreadPoolBannerHandler;
import cn.hippo4j.core.toolkit.IdentifyUtil;
import cn.hippo4j.core.toolkit.inet.InetUtils;
import cn.hippo4j.message.api.NotifyConfigBuilder;
import cn.hippo4j.message.config.MessageConfiguration;
import cn.hippo4j.message.service.AlarmControlHandler;
import cn.hippo4j.message.service.DefaultThreadPoolCheckAlarmHandler;
import cn.hippo4j.message.service.DefaultThreadPoolConfigChangeHandler;
import cn.hippo4j.message.service.Hippo4jBaseSendMessageService;
import cn.hippo4j.message.service.Hippo4jSendMessageService;
import cn.hippo4j.springboot.starter.adapter.web.WebAdapterConfiguration;
import cn.hippo4j.springboot.starter.controller.ThreadPoolAdapterController;
import cn.hippo4j.springboot.starter.core.BaseThreadDetailStateHandler;
import cn.hippo4j.springboot.starter.core.ClientShutdown;
import cn.hippo4j.springboot.starter.core.ClientWorker;
import cn.hippo4j.springboot.starter.core.DynamicThreadPoolSubscribeConfig;
import cn.hippo4j.springboot.starter.core.ServerThreadPoolDynamicRefresh;
import cn.hippo4j.springboot.starter.core.ThreadPoolAdapterRegister;
import cn.hippo4j.springboot.starter.event.ApplicationContentPostProcessor;
import cn.hippo4j.springboot.starter.monitor.ReportingEventExecutor;
import cn.hippo4j.springboot.starter.monitor.collect.RunTimeInfoCollector;
import cn.hippo4j.springboot.starter.monitor.send.MessageSender;
import cn.hippo4j.springboot.starter.monitor.send.http.HttpConnectSender;
import cn.hippo4j.springboot.starter.notify.ServerModeNotifyConfigBuilder;
import cn.hippo4j.springboot.starter.remote.HttpAgent;
import cn.hippo4j.springboot.starter.remote.HttpScheduledHealthCheck;
import cn.hippo4j.springboot.starter.remote.ServerHealthCheck;
import cn.hippo4j.springboot.starter.remote.ServerHttpAgent;
import cn.hippo4j.springboot.starter.support.AdaptedThreadPoolDestroyPostProcessor;
import cn.hippo4j.springboot.starter.support.DynamicThreadPoolConfigService;
import cn.hippo4j.springboot.starter.support.DynamicThreadPoolPostProcessor;
import cn.hippo4j.springboot.starter.support.ThreadPoolPluginRegisterPostProcessor;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Dynamic thread-pool auto-configuration.
 *
 * <p><b>NOTE:</b>
 * {@code cn.hippo4j.springboot.starter.config.DynamicThreadPoolAutoConfiguration} is used in the
 * hippo4j-spring-boot-starter-adapter-hystrix module to determine the condition, see
 * {@code cn.hippo4j.springboot.starter.adapter.hystrix.HystrixAdapterAutoConfiguration}, please
 * note the subsequent modification.
 */
@Configuration
@AllArgsConstructor
@ConditionalOnBean(MarkerConfiguration.Marker.class)
@EnableConfigurationProperties(BootstrapProperties.class)
@ConditionalOnProperty(prefix = BootstrapProperties.PREFIX, value = "enable", matchIfMissing = true, havingValue = "true")
@ImportAutoConfiguration({WebAdapterConfiguration.class, NettyClientConfiguration.class, DiscoveryConfiguration.class, MessageConfiguration.class, UtilAutoConfiguration.class})
public class CustomDynamicThreadPoolAutoConfiguration implements ApplicationContextAware {
    private ApplicationContext applicationContext;

    private final BootstrapProperties properties;

    private final ConfigurableEnvironment environment;

    @Bean
    public DynamicThreadPoolBannerHandler threadPoolBannerHandler(ObjectProvider<BuildProperties> buildProperties) {
        return new DynamicThreadPoolBannerHandler(properties, buildProperties.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationContextHolder hippo4jApplicationContextHolder() {
        return new ApplicationContextHolder();
    }

    @Bean
    public ClientWorker hippo4jClientWorker(HttpAgent httpAgent,
                                            InetUtils hippo4jInetUtils,
                                            ServerHealthCheck serverHealthCheck,
                                            DynamicThreadPoolBannerHandler dynamicThreadPoolBannerHandlers,
                                            ClientShutdown hippo4jClientShutdown) {
        String identify = IdentifyUtil.generate(environment, hippo4jInetUtils);
        return new ClientWorker(httpAgent, identify, serverHealthCheck, dynamicThreadPoolBannerHandlers.getVersion(), hippo4jClientShutdown);
    }

    @Bean
    @SuppressWarnings("all")
    public DynamicThreadPoolService dynamicThreadPoolConfigService(HttpAgent httpAgent,
                                                                   ServerHealthCheck serverHealthCheck,
                                                                   ServerModeNotifyConfigBuilder serverModeNotifyConfigBuilder,
                                                                   Hippo4jBaseSendMessageService hippo4jBaseSendMessageService,
                                                                   DynamicThreadPoolSubscribeConfig dynamicThreadPoolSubscribeConfig) {
        return new DynamicThreadPoolConfigService(httpAgent, properties, serverModeNotifyConfigBuilder, hippo4jBaseSendMessageService, dynamicThreadPoolSubscribeConfig);
    }

    @Bean
    public AdaptedThreadPoolDestroyPostProcessor adaptedThreadPoolDestroyPostProcessor(ApplicationContext applicationContext) {
        return new AdaptedThreadPoolDestroyPostProcessor(applicationContext);
    }

    @Bean
    @SuppressWarnings("all")
    public DynamicThreadPoolPostProcessor threadPoolBeanPostProcessor(HttpAgent httpAgent,
                                                                      ApplicationContextHolder hippo4jApplicationContextHolder,
                                                                      DynamicThreadPoolSubscribeConfig dynamicThreadPoolSubscribeConfig) {
        return new DynamicThreadPoolPostProcessor(properties, httpAgent, dynamicThreadPoolSubscribeConfig);
    }

    @Bean
    @ConditionalOnMissingBean(value = ThreadDetailState.class)
    public ThreadDetailState baseThreadDetailStateHandler() {
        return new BaseThreadDetailStateHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("all")
    public MessageSender messageSender(HttpAgent httpAgent) {
        return new HttpConnectSender(httpAgent);
    }

    @Bean
    public ReportingEventExecutor reportingEventExecutor(BootstrapProperties properties,
                                                         MessageSender messageSender,
                                                         ServerHealthCheck serverHealthCheck) {
        return new ReportingEventExecutor(properties, messageSender, serverHealthCheck);
    }

    @Bean
    @SuppressWarnings("all")
    public ServerHealthCheck httpScheduledHealthCheck(HttpAgent httpAgent) {
        return new HttpScheduledHealthCheck(httpAgent);
    }

    @Bean
    public RunTimeInfoCollector runTimeInfoCollector() {
        return new RunTimeInfoCollector(properties);
    }

    @Bean
    @SuppressWarnings("all")
    public ThreadPoolAdapterController threadPoolAdapterController(InetUtils hippo4jInetUtils) {
        return new ThreadPoolAdapterController(environment, hippo4jInetUtils);
    }

    @Bean
    public ThreadPoolAdapterBeanContainer threadPoolAdapterBeanContainer() {
        return new ThreadPoolAdapterBeanContainer();
    }

    @Bean
    public ApplicationContentPostProcessor applicationContentPostProcessor() {
        ApplicationContentPostProcessor applicationContentPostProcessor =
                new ApplicationContentPostProcessor();
        applicationContentPostProcessor.setApplicationContext(this.applicationContext);
        return applicationContentPostProcessor;
    }

    @Bean
    @SuppressWarnings("all")
    public ThreadPoolAdapterRegister threadPoolAdapterRegister(HttpAgent httpAgent,
                                                               InetUtils hippo4jInetUtils) {
        return new ThreadPoolAdapterRegister(httpAgent, properties, environment, hippo4jInetUtils);
    }

    @Bean
    public NotifyConfigBuilder serverNotifyConfigBuilder(HttpAgent httpAgent,
                                                         BootstrapProperties properties,
                                                         AlarmControlHandler alarmControlHandler) {
        return new ServerModeNotifyConfigBuilder(httpAgent, properties, alarmControlHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolCheckAlarm defaultThreadPoolCheckAlarmHandler(Hippo4jSendMessageService hippo4jSendMessageService) {
        return new DefaultThreadPoolCheckAlarmHandler(hippo4jSendMessageService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolConfigChange defaultThreadPoolConfigChangeHandler(Hippo4jSendMessageService hippo4jSendMessageService) {
        return new DefaultThreadPoolConfigChangeHandler(hippo4jSendMessageService);
    }

    @Bean
    public ThreadPoolDynamicRefresh threadPoolDynamicRefresh(ThreadPoolConfigChange threadPoolConfigChange) {
        return new ServerThreadPoolDynamicRefresh(threadPoolConfigChange);
    }

    @Bean
    public DynamicThreadPoolSubscribeConfig dynamicThreadPoolSubscribeConfig(ThreadPoolDynamicRefresh threadPoolDynamicRefresh,
                                                                             ClientWorker clientWorker) {
        return new DynamicThreadPoolSubscribeConfig(threadPoolDynamicRefresh, clientWorker, properties);
    }

    @Bean
    public HttpAgent httpAgent(BootstrapProperties properties) {
        return new ServerHttpAgent(properties);
    }

    @Bean
    public ThreadPoolPluginRegisterPostProcessor threadPoolPluginRegisterPostProcessor() {
        return new ThreadPoolPluginRegisterPostProcessor();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
