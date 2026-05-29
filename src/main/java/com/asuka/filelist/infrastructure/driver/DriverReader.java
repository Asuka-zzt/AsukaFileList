package com.asuka.filelist.infrastructure.driver;

import com.asuka.filelist.domain.fs.FileLink;
import com.asuka.filelist.domain.fs.FileObject;

import java.util.List;

public interface DriverReader {

    List<FileObject> list(DriverContext context, FileObject dir, ListArgs args);

    FileLink link(DriverContext context, FileObject file, LinkArgs args);
}
