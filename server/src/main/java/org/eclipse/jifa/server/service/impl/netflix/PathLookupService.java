package org.eclipse.jifa.server.service.impl.netflix;

import java.nio.file.Path;

import org.eclipse.jifa.server.enums.FileType;

public interface PathLookupService
{
    Path lookupFile(Path basePath, FileType type, String name);
}
