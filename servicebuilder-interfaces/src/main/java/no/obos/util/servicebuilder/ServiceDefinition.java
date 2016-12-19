package no.obos.util.servicebuilder;

import com.google.common.collect.ImmutableList;

import java.util.List;

public interface ServiceDefinition {
    String getName();

    List<Class> getResources();

    default JsonConfig getJsonConfig() {
        return JsonConfig.standard;
    }

    static ServiceDefinition simple (final String name, final Class ... resources) {
        return new ServiceDefinition() {
            ImmutableList<Class> classes = ImmutableList.copyOf(resources);
            @Override
            public String getName() {
                return name;
            }

            @Override
            public List<Class> getResources() {
                return classes;
            }
        };
    }
}
