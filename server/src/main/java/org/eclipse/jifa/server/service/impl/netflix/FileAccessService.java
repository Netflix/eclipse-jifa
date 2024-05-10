package org.eclipse.jifa.server.service.impl.netflix;

import org.eclipse.jifa.server.domain.entity.shared.file.BaseFileEntity;

public interface FileAccessService {

    public void checkAuthorityForCurrentUser(BaseFileEntity file);
}
