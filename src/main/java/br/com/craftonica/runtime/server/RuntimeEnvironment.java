package br.com.craftonica.runtime.server;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves only external sandbox/runtime artifacts; it never falls back to in-process execution. */
final class RuntimeEnvironment {
    final File launcher;
    final File javaHome;
    final File workerJar;

    private RuntimeEnvironment(File launcher, File javaHome, File workerJar) {
        this.launcher = launcher;
        this.javaHome = javaHome;
        this.workerJar = workerJar;
    }

    static RuntimeEnvironment discover() throws IOException {
        File source = codeSource();
        File launcher = explicit("craftonica.runtime.launcher", "CRAFTONICA_RUNTIME_LAUNCHER");
        File worker = explicit("craftonica.runtime.workerJar", "CRAFTONICA_RUNTIME_WORKER_JAR");
        File javaHome = explicit("craftonica.runtime.javaHome", "CRAFTONICA_RUNTIME_JAVA_HOME");
        if (javaHome == null) javaHome = new File(System.getProperty("java.home", ""));

        List<File> roots = roots(source);
        if (launcher == null) launcher = firstExisting(roots, "scripts/runtime/launch-worker.sh",
                "craftonica-runtime/launch-worker.sh", "launch-worker.sh");
        if (worker == null) worker = findWorker(source, roots);

        launcher = canonicalFile(launcher, "sandbox launcher");
        worker = canonicalFile(worker, "runtime worker JAR");
        javaHome = canonicalDirectory(javaHome, "Java home");
        if (!launcher.canExecute()) throw new IOException("Craftonica runtime sandbox launcher is not executable: " + launcher);
        if (!new File(javaHome, "bin/java").canExecute()) throw new IOException("Craftonica runtime Java is not executable: " + javaHome);
        return new RuntimeEnvironment(launcher, javaHome, worker);
    }

    private static File explicit(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.trim().length() == 0) value = System.getenv(environment);
        return value == null || value.trim().length() == 0 ? null : new File(value.trim());
    }

    private static File codeSource() throws IOException {
        try {
            URL location = RuntimeEnvironment.class.getProtectionDomain().getCodeSource().getLocation();
            if ("jar".equals(location.getProtocol())) {
                location = ((JarURLConnection) location.openConnection()).getJarFileURL();
            }
            if (!"file".equals(location.getProtocol())) {
                throw new IOException("Unsupported Craftonica code source: " + location.getProtocol());
            }
            return new File(location.toURI());
        } catch (URISyntaxException failure) {
            throw new IOException("Cannot resolve the Craftonica code source", failure);
        }
    }

    private static List<File> roots(File source) {
        List<File> roots = new ArrayList<File>();
        addAncestors(roots, new File(System.getProperty("user.dir", ".")), 6);
        addAncestors(roots, source.isDirectory() ? source : source.getParentFile(), 8);
        return roots;
    }

    private static void addAncestors(List<File> roots, File start, int limit) {
        File current = start;
        for (int i = 0; current != null && i < limit; i++, current = current.getParentFile()) {
            if (!roots.contains(current)) roots.add(current);
        }
    }

    private static File firstExisting(List<File> roots, String... relativePaths) {
        for (File root : roots) for (String relative : relativePaths) {
            File candidate = new File(root, relative);
            if (candidate.isFile()) return candidate;
        }
        return null;
    }

    private static File findWorker(File source, List<File> roots) {
        List<File> directories = new ArrayList<File>();
        if (source.isFile()) directories.add(source.getParentFile());
        for (File root : roots) {
            directories.add(new File(root, "build/runtime"));
            directories.add(new File(root, "craftonica-runtime"));
        }
        List<File> matches = new ArrayList<File>();
        for (File directory : directories) {
            File[] files = directory == null ? null : directory.listFiles();
            if (files == null) continue;
            for (File file : files) {
                String name = file.getName();
                if (file.isFile() && name.startsWith("craftonica-") && name.endsWith("-runtime-worker.jar")) matches.add(file);
            }
        }
        Collections.sort(matches);
        return matches.isEmpty() ? null : matches.get(matches.size() - 1);
    }

    private static File canonicalFile(File value, String label) throws IOException {
        if (value == null || !value.isFile()) {
            throw new IOException("Missing " + label + "; install the separate runtime-worker JAR and launcher or configure craftonica.runtime.*");
        }
        return value.getCanonicalFile();
    }

    private static File canonicalDirectory(File value, String label) throws IOException {
        if (value == null || !value.isDirectory()) throw new IOException("Missing " + label + ": " + value);
        return value.getCanonicalFile();
    }
}
