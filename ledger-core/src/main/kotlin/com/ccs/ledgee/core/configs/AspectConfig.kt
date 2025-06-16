package com.ccs.ledgee.core.configs

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

@EnableAspectJAutoProxy(proxyTargetClass = true)
@Configuration
class AspectConfig