package org.elasticsearch.plugin.syslog;

import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.syslog.SyslogModule;
import org.elasticsearch.syslog.SyslogService;

import java.util.ArrayList;
import java.util.Collection;

public class SyslogPlugin extends Plugin {

    private final Settings settings;

    public SyslogPlugin(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String name() {
        return "syslog";
    }

    @Override
    public String description() {
        return "Syslog plugin";
    }

    @Override
    public Collection<Module> nodeModules() {
        Collection<Module> modules = new ArrayList<>();
        if (settings.getAsBoolean("plugins.syslog.enabled", true)) {
            modules.add(new SyslogModule());
        }
        return modules;
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> nodeServices() {
        Collection<Class<? extends LifecycleComponent>> services = new ArrayList<>();
        if (settings.getAsBoolean("plugins.syslog.enabled", true)) {
            services.add(SyslogService.class);
        }
        return services;
    }

}
