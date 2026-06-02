package me.blueat.logmaker.core.util;

import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;

import java.nio.charset.StandardCharsets;

public final class VelocityTemplateUtil {
    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    private VelocityTemplateUtil() {
    }

    public static VelocityEngine createSecureEngine(int parserPoolSize) {
        VelocityEngine ve = new VelocityEngine();
        ve.setProperty("resource.loaders", "string");
        ve.setProperty("resource.loader.string.class", StringResourceLoader.class.getName());
        ve.setProperty("resource.loader.string.repository.static", false);
        ve.setProperty("resource.loader.string.cache", false);
        ve.setProperty("resource.default_encoding", UTF_8);
        ve.setProperty("introspector.uberspect.class", "org.apache.velocity.util.introspection.SecureUberspector");
        ve.setProperty("introspector.restrict.packages", "java.lang.reflect,java.lang.Runtime,java.lang.Process,java.lang.System");
        ve.setProperty("introspector.restrict.classes", "java.lang.Class,java.lang.ClassLoader,java.lang.Thread,java.lang.Compiler,java.lang.Runtime,java.lang.System");
        ve.setProperty("parser.pool.size", parserPoolSize);
        ve.init();
        return ve;
    }

    public static Template compile(VelocityEngine ve, String name, String source) {
        String resourceName = toResourceName(name);
        getRepository(ve).putStringResource(resourceName, source, UTF_8);
        return ve.getTemplate(resourceName, UTF_8);
    }

    private static StringResourceRepository getRepository(VelocityEngine ve) {
        Object repository = ve.getApplicationAttribute(StringResourceLoader.REPOSITORY_NAME_DEFAULT);
        if (repository instanceof StringResourceRepository stringResourceRepository) {
            return stringResourceRepository;
        }
        throw new IllegalStateException("Velocity string resource repository is not initialized");
    }

    private static String toResourceName(String name) {
        if (name == null || name.isBlank()) {
            return "template";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
