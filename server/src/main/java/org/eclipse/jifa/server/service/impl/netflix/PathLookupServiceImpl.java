package org.eclipse.jifa.server.service.impl.netflix;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jifa.server.enums.FileType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PathLookupServiceImpl implements PathLookupService {
    @Value("${fc.ssmOutputPath}")
    private String ssmOutputPath;

    @Override
    public Path lookupFile(Path basePath, FileType type, String name)
    {
        if (name.startsWith("s3!") && type == FileType.HEAP_DUMP) {
            InstanceCommand ic = InstanceCommand.parseParam(name);
            String path = String.format("%s/heapdump/%s/%s/%s", ssmOutputPath, ic.instanceId, ic.commandId, ic.pid);
            Path result = Paths.get(path).resolve("heapdump.hprof");
            log.info("Path lookup (Netflix) for {} of type {} resolved to {}", name, type, result);
            return result;
        }

        Path result = basePath.resolve(type.getStorageDirectoryName()).resolve(name).resolve(name);
        log.info("Path lookup (default) for {} of type {} resolved to {}", name, type, result);
        return result;
    }
}
