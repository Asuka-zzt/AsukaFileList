package com.asuka.filelist.application.storage;

import com.asuka.filelist.domain.storage.Storage;
import com.asuka.filelist.infrastructure.driver.DriverConfig;
import com.asuka.filelist.infrastructure.driver.DriverContext;
import com.asuka.filelist.infrastructure.driver.DriverInfo;
import com.asuka.filelist.infrastructure.driver.StorageDriver;
import com.asuka.filelist.infrastructure.driver.StorageDriverFactory;
import com.asuka.filelist.infrastructure.driver.StorageDriverRegistry;
import com.asuka.filelist.domain.fs.FileLink;
import com.asuka.filelist.domain.fs.FileObject;
import com.asuka.filelist.infrastructure.driver.LinkArgs;
import com.asuka.filelist.infrastructure.driver.ListArgs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MountedStorageRegistry 最长前缀匹配测试。
 */
class MountedStorageRegistryTest {

    /**
     * 请求路径命中多个挂载点时选择最长前缀。
     */
    @Test
    void matchLongestPrefix_prefersMostSpecificMountPath() {
        MountedStorageRegistry registry = new MountedStorageRegistry(new StorageDriverRegistry(List.of(factory())));
        registry.mount(storage(1L, "/docs"));
        registry.mount(storage(2L, "/docs/sub"));

        Optional<MountedStorageRuntime> matched = registry.matchLongestPrefix("/docs/sub/a.txt");

        assertThat(matched).isPresent();
        assertThat(matched.get().storage().id()).isEqualTo(2L);
    }

    /**
     * 卸载后不能再匹配到对应 storage。
     */
    @Test
    void unmount_removesRuntime() {
        MountedStorageRegistry registry = new MountedStorageRegistry(new StorageDriverRegistry(List.of(factory())));
        registry.mount(storage(1L, "/docs"));

        registry.unmount(1L);

        assertThat(registry.matchLongestPrefix("/docs/a.txt")).isEmpty();
    }

    /**
     * 构造测试存储。
     */
    private Storage storage(Long id, String mountPath) {
        return new Storage(id, mountPath, 0, "Fake", 30, "work", "{}", "", null,
                false, false, false, "name", "asc", "front", false, "proxy", false);
    }

    /**
     * 构造测试驱动工厂。
     */
    private StorageDriverFactory factory() {
        return new StorageDriverFactory() {
            @Override
            public String name() {
                return "Fake";
            }

            @Override
            public DriverInfo info() {
                return new DriverInfo(new DriverConfig("Fake", true, false, false, false, true, "/", false), List.of());
            }

            @Override
            public StorageDriver create() {
                return new FakeDriver();
            }
        };
    }

    /**
     * 最小测试驱动。
     */
    private static class FakeDriver implements StorageDriver {

        private Storage storage;

        @Override
        public DriverConfig config() {
            return new DriverConfig("Fake", true, false, false, false, true, "/", false);
        }

        @Override
        public Storage storage() {
            return storage;
        }

        @Override
        public void setStorage(Storage storage) {
            this.storage = storage;
        }

        @Override
        public Object addition() {
            return Map.of();
        }

        @Override
        public void init(DriverContext context) {
        }

        @Override
        public void drop(DriverContext context) {
        }

        @Override
        public List<FileObject> list(DriverContext context, FileObject dir, ListArgs args) {
            return List.of();
        }

        @Override
        public FileLink link(DriverContext context, FileObject file, LinkArgs args) {
            return null;
        }
    }
}
