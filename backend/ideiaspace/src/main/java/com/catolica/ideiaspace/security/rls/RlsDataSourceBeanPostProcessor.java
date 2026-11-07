package com.catolica.ideiaspace.security.rls;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class RlsDataSourceBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource && !(dataSource instanceof RlsDataSource)) {
            return new RlsDataSource(dataSource);
        }
        return bean;
    }
}
