package org.eclipse.jifa.server.service.impl.netflix;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstanceCommand {
    final String instanceId;
    final String commandId;
    final long pid;

    final static Pattern VALID_REGEX = Pattern.compile("[0-9a-z-]+");

    public InstanceCommand(final String instanceId, final String commandId, final long pid) {
        this.instanceId = instanceId;
        this.commandId = commandId;
        this.pid = pid;
    }

    public static InstanceCommand parseParam(String param) {
        if (param == null) {
            return null;
        }

        final String[] parts = param.split("!");

        if (parts.length != 4 || !parts[0].equals("s3")) {
            return null;
        }

        // validate the data; three requirements:
        // 1) instance id should be only alpha and hyphen
        // 2) command id is a uuid
        Matcher instanceIdMatcher = VALID_REGEX.matcher(parts[1]);
        Matcher commandIdMatcher = VALID_REGEX.matcher(parts[2]);
        if (!instanceIdMatcher.matches() || !commandIdMatcher.matches()) {
            return null;
        }

        // 3) pid should be parsable as a number (only)
        final long pid;
        try {
            pid = Long.valueOf(parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }

        return new InstanceCommand(parts[1], parts[2], pid);
    }

    public String toString() {
        return "InstanceCommand{" +
            "instanceId='" + instanceId + '\'' +
            ", commandId='" + commandId + '\'' +
            ", pid=" + pid +
            '}';
    }
}