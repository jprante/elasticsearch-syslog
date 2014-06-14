package org.elasticsearch.plugin.syslog;

import org.elasticsearch.common.inject.AbstractModule;

public class SyslogModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SyslogService.class).asEagerSingleton();
    }
}
