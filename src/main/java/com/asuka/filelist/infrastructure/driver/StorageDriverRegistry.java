package com.asuka.filelist.infrastructure.driver;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StorageDriverRegistry {

    private final Map<String, StorageDriverFactory> factories;

    public StorageDriverRegistry(Collection<StorageDriverFactory> factories) {
        this.factories = factories.stream()
                .collect(Collectors.toUnmodifiableMap(StorageDriverFactory::name, Function.identity()));
    }

    public Optional<StorageDriverFactory> findFactory(String name) {
        return Optional.ofNullable(factories.get(name));
    }

    public Collection<String> driverNames() {
        return factories.keySet();
    }
}
