package com.wanyue.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CloudApplicationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;

	@Test
	void contextLoads() {
	}

}
