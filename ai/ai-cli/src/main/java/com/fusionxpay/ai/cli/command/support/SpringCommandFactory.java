package com.fusionxpay.ai.cli.command.support;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
public class SpringCommandFactory implements CommandLine.IFactory {

    private final BeanFactory beanFactory;

    public SpringCommandFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public <K> K create(Class<K> cls) {
        return beanFactory.getBean(cls);
    }
}
