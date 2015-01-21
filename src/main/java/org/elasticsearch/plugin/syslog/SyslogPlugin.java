package org.elasticsearch.plugin.syslog;

import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.syslog.SyslogModule;
import org.elasticsearch.syslog.SyslogService;

import java.util.Collection;

import static org.elasticsearch.common.collect.Lists.newArrayList;

/**
 * Syslog plugin
 */
public class SyslogPlugin extends AbstractPlugin {

    private final Settings settings;

    public SyslogPlugin(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String name() {
        return "syslog-"
                + Build.getInstance().getVersion() + "-"
                + Build.getInstance().getShortHash();
    }

    @Override
    public String description() {
        return "Syslog plugin";
    }

    @Override
    public Collection<Class<? extends Module>> modules() {
        Collection<Class<? extends Module>> modules = newArrayList();
        if (settings.getAsBoolean("plugins.syslog.enabled", true)) {
            modules.add(SyslogModule.class);
        }
        return modules;
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> services() {
        Collection<Class<? extends LifecycleComponent>> services = newArrayList();
        if (settings.getAsBoolean("plugins.syslog.enabled", true)) {
            services.add(SyslogService.class);
        }
        return services;
    }

}
