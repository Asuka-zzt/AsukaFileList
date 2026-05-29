package com.asuka.filelist.infrastructure.driver;

public interface StorageDriverFactory {

    String name();

    StorageDriver create();
}
