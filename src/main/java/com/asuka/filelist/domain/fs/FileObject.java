package com.asuka.filelist.domain.fs;

import java.time.Instant;
import java.util.Map;

public interface FileObject {

    String id();

    String path();

    String name();

    long size();

    Instant modifiedAt();

    Instant createdAt();

    boolean directory();

    Map<String, String> hashInfo();
}
