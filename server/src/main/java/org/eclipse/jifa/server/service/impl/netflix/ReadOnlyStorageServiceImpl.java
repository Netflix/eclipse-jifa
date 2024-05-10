/********************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.jifa.server.service.impl.netflix;

import jakarta.annotation.PostConstruct;
import org.eclipse.jifa.common.util.Validate;
import org.eclipse.jifa.server.ConfigurationAccessor;
import org.eclipse.jifa.server.domain.dto.FileTransferRequest;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.service.StorageService;
import org.eclipse.jifa.server.support.FileTransferListener;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Primary
@Service
public class ReadOnlyStorageServiceImpl extends ConfigurationAccessor implements StorageService {
    private Path basePath;
    private final PathLookupService pathLookupService;

    public ReadOnlyStorageServiceImpl(PathLookupService pathLookupService) {
        this.pathLookupService = pathLookupService;
    }

    @PostConstruct
    private void init() {
        basePath = config.getStoragePath();
        Validate.isTrue(Files.isDirectory(basePath));
        Validate.notNull(pathLookupService, "pathLookupService");
    }

    @Override
    public long getAvailableSpace() throws IOException {
        return Files.getFileStore(basePath).getUsableSpace();
    }

    @Override
    public long getTotalSpace() throws IOException {
        return Files.getFileStore(basePath).getTotalSpace();
    }

    @Override
    public void handleTransfer(FileTransferRequest request, String destFilename, FileTransferListener listener) {
        listener.onError(new UnsupportedOperationException("Read-only storage"));
    }

    @Override
    public long handleUpload(FileType type, MultipartFile file, String destFilename) throws IOException {
        throw new UnsupportedOperationException("Read-only storage");
    }

    @Override
    public void handleLocalFile(FileType type, Path path, String destFilename) throws IOException {
        throw new UnsupportedOperationException("Read-only storage");
    }

    @Override
    public Path locationOf(FileType type, String name) {
        return pathLookupService.lookupFile(basePath, type, name);
    }

    @Override
    public void scavenge(FileType type, String name) {
        throw new UnsupportedOperationException("Read-only storage");
    }

    @Override
    public Map<FileType, Set<String>> getAllFiles() {
        return Collections.emptyMap();
    }

    @Override
    public boolean available()
    {
        throw new UnsupportedOperationException("Read-only storage");
    }
}
