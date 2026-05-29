package com.asuka.filelist.infrastructure.driver;

import com.asuka.filelist.domain.fs.FileObject;

public interface DriverRootProvider {

    FileObject getRoot(DriverContext context);
}
