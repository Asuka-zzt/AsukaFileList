package com.asuka.filelist.infrastructure.driver;

import com.asuka.filelist.domain.storage.Storage;

public interface DriverMeta {

    DriverConfig config();

    Storage storage();

    void setStorage(Storage storage);

    Object addition();

    void init(DriverContext context);

    void drop(DriverContext context);
}
