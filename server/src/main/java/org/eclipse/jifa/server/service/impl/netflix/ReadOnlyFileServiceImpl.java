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
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.common.domain.vo.PageView;
import org.eclipse.jifa.common.util.Validate;
import org.eclipse.jifa.server.ConfigurationAccessor;
import org.eclipse.jifa.server.domain.converter.FileViewConverter;
import org.eclipse.jifa.server.domain.dto.FileTransferProgress;
import org.eclipse.jifa.server.domain.dto.FileTransferRequest;
import org.eclipse.jifa.server.domain.dto.FileView;
import org.eclipse.jifa.server.domain.dto.NamedResource;
import org.eclipse.jifa.server.domain.entity.cluster.StaticWorkerEntity;
import org.eclipse.jifa.server.domain.entity.shared.file.FileEntity;
import org.eclipse.jifa.server.enums.FileType;
import org.eclipse.jifa.server.service.FileService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.eclipse.jifa.server.enums.Role.ELASTIC_WORKER;
import static org.eclipse.jifa.server.enums.Role.MASTER;
import static org.eclipse.jifa.server.enums.Role.STANDALONE_WORKER;

@Primary
@SuppressWarnings("DataFlowIssue")
@Component
public class ReadOnlyFileServiceImpl extends ConfigurationAccessor implements FileService {

    private Path basePath;
    private final FileAccessService fileUserAccessService;
    private final PathLookupService pathLookupService;
    private final ReadAheadService readAheadService;

    public ReadOnlyFileServiceImpl(FileAccessService fileUserAccessService, PathLookupService pathLookupService, ReadAheadService readAheadService) {
        this.fileUserAccessService = fileUserAccessService;
        this.pathLookupService = pathLookupService;
        this.readAheadService = readAheadService;
    }

    @PostConstruct
    private void init() {
        basePath = config.getStoragePath();
        Validate.isTrue(Files.isDirectory(basePath));
    }

    @Override
    public PageView<FileView> getUserFileViews(FileType type, int page, int pageSize) {
        mustBe(MASTER, STANDALONE_WORKER);
        return new PageView<>(page, pageSize, 0, List.of());
    }

    @Override
    public FileView getFileViewById(long fileId) {
        mustBe(MASTER, STANDALONE_WORKER);
        throw new IllegalArgumentException("Read-only storage, does not understand file id");
    }

    @Override
    public FileView getFileViewByUniqueName(String uniqueName) {
        mustBe(MASTER, STANDALONE_WORKER);

        FileEntity entity = getFileByUniqueName(uniqueName, FileType.HEAP_DUMP);

        return FileViewConverter.convert(entity);
    }

    @Override
    public void deleteById(long fileId) {
        mustNotBe(ELASTIC_WORKER);
        throw new UnsupportedOperationException("Read-only storage");
    }

    @Override
    public FileEntity getFileByUniqueName(String uniqueName, FileType expectedFileType) {
        Path realPath = pathLookupService.lookupFile(basePath, FileType.HEAP_DUMP, uniqueName);

        try {
            BasicFileAttributes attrs = Files.readAttributes(realPath, BasicFileAttributes.class);

            LocalDateTime createdTime = LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault());

            FileEntity file = new FileEntity();
            // TODO this id we send back, is used for downloads; what should the
            //      value be so that we can look up the file again? do we need
            //      a storage spot or can we harvest it from the instance id or
            //      something
            //      maybe a local cache, but does not allow for clustering
            file.setId(-1L);
            file.setUniqueName(uniqueName);
            file.setOriginalName("heapdump.hprof");
            file.setType(FileType.HEAP_DUMP);
            file.setSize(attrs.size());
            file.setCreatedTime(createdTime);

            fileUserAccessService.checkAuthorityForCurrentUser(file);

            readAheadService.issueForPath(realPath);

            return file;
        } catch (IOException e) {
            throw new IllegalArgumentException("file information not present");
        }
    }

    @Override
    public long handleTransferRequest(FileTransferRequest request) {
        mustNotBe(ELASTIC_WORKER);
        throw new UnsupportedOperationException("Read-only storage");
    }

    @Override
    public FileTransferProgress getTransferProgress(long transferringFileId) {
        mustBe(MASTER, STANDALONE_WORKER);
        throw new UnsupportedOperationException("Read-only storage");
    }

    @Override
    public long handleUploadRequest(FileType type, MultipartFile file) throws Throwable {
        mustNotBe(ELASTIC_WORKER);
        throw new UnsupportedOperationException("Read-only storage");
    }

    @Override
    public String handleLocalFileRequest(FileType type, Path path) throws IOException {
        mustBe(STANDALONE_WORKER);
        throw new UnsupportedOperationException("Read-only storage");
    }

    @Override
    public NamedResource handleDownloadRequest(long fileId) throws Throwable {
        mustNotBe(ELASTIC_WORKER);
        // TODO we should be able to download this, but, we do not have a way
        //      to link the fileId to the actual file on disk
        throw new UnsupportedOperationException("Read-only storage");
    }

    @Override
    public void deleteOldestFile() {
        throw new UnsupportedOperationException("Read-only storage");
    }

    @Override
    public Optional<StaticWorkerEntity> getStaticWorkerByFile(FileEntity file)
    {
        throw new UnsupportedOperationException("Read-only storage");
    }
}
