package com.asuka.filelist.common.path;

import com.asuka.filelist.common.exception.BusinessException;
import com.asuka.filelist.common.exception.ErrorCode;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.StringJoiner;

public final class PathUtils {

    private PathUtils() {
    }

    public static String fixAndCleanPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        String normalized = rawPath.replace('\\', '/').trim();
        Deque<String> segments = new ArrayDeque<>();
        for (String part : normalized.split("/+")) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
                continue;
            }
            segments.addLast(part);
        }
        if (segments.isEmpty()) {
            return "/";
        }
        StringJoiner joiner = new StringJoiner("/", "/", "");
        segments.forEach(joiner::add);
        return joiner.toString();
    }

    public static String joinBasePath(String basePath, String requestPath) {
        String base = fixAndCleanPath(basePath);
        String request = fixAndCleanPath(requestPath);
        if ("/".equals(base)) {
            return request;
        }
        if ("/".equals(request)) {
            return base;
        }
        return fixAndCleanPath(base + "/" + request.substring(1));
    }

    public static boolean pathEquals(String left, String right) {
        return Objects.equals(fixAndCleanPath(left), fixAndCleanPath(right));
    }

    public static boolean isSubPath(String parent, String child) {
        String normalizedParent = fixAndCleanPath(parent);
        String normalizedChild = fixAndCleanPath(child);
        if ("/".equals(normalizedParent)) {
            return true;
        }
        return normalizedChild.equals(normalizedParent) || normalizedChild.startsWith(normalizedParent + "/");
    }

    public static void validateNameComponent(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Name cannot be empty");
        }
        if (name.contains("/") || name.contains("\\") || ".".equals(name) || "..".equals(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid path component: " + name);
        }
        try {
            Path.of(name);
        } catch (InvalidPathException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid path component: " + name);
        }
    }
}
