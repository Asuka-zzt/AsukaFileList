package com.asuka.filelist.infrastructure.driver;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Collection;
import java.util.List;
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
        return factories.keySet().stream().sorted().toList();
    }

    /**
     * 返回全部驱动描述，按名称稳定排序。
     */
    public List<DriverInfo> driverInfos() {
        return factories.values().stream()
                .sorted(Comparator.comparing(StorageDriverFactory::name))
                .map(StorageDriverFactory::info)
                .toList();
    }
}
