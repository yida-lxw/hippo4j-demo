package com.yida.demo.task;

import com.yida.demo.utils.SpringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

@Component
public class ScheduledTask {
    private Logger logger= LoggerFactory.getLogger(ScheduledTask.class);

    @Async("messageProduceDynamicExecutor")
    @Scheduled(cron = "0 0/1 * * * ?")
    public void testOne(){
        logger.info("每分钟执行一次");
        ThreadPoolExecutor messageProduceDynamicExecutor = SpringUtils.getBean("messageProduceDynamicExecutor",
                ThreadPoolExecutor.class);
        int corePoolSize = messageProduceDynamicExecutor.getCorePoolSize();
        int maxPoolSize = messageProduceDynamicExecutor.getMaximumPoolSize();
        logger.info("produce-threadPool-->corePoolSize:{},maxPoolSize:{}", corePoolSize, maxPoolSize);
    }

    @Async("messageConsumeDynamicExecutor")
    @Scheduled(fixedDelay = 30000)
    public void testTwo(){
        logger.info("每30秒执行一次");
        ThreadPoolExecutor messageConsumeDynamicExecutor = SpringUtils.getBean("messageConsumeDynamicExecutor",
                ThreadPoolExecutor.class);
        int corePoolSize = messageConsumeDynamicExecutor.getCorePoolSize();
        int maxPoolSize = messageConsumeDynamicExecutor.getMaximumPoolSize();
        logger.info("consume-threadPool-->corePoolSize:{},maxPoolSize:{}", corePoolSize, maxPoolSize);
    }
}
