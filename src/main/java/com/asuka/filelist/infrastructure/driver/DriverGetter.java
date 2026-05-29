package com.asuka.filelist.infrastructure.driver;

import com.asuka.filelist.domain.fs.FileObject;

public interface DriverGetter {

    FileObject get(DriverContext context, String actualPath);
}
