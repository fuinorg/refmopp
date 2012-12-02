package org.fuin.refmopp;

import static org.fest.assertions.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.log4j.BasicConfigurator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.emftext.language.java.JavaClasspath;
import org.emftext.language.java.resource.JaMoPPUtil;
import org.fuin.objects4j.Label;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reflections.Reflections;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;

public class JamoppAdapterTest {

    @BeforeClass
    public static void beforeClass() {
        BasicConfigurator.configure();
        JaMoPPUtil.initialize();
    }

    @Test
    public void testGetTypesAnnotatedWith() throws IOException {

        // PREPARE
        final File srcRoot = new File("src/test/java");
        final File jarDir = new File("src/test");
        final File jarFile = new File(jarDir, "objects4j-0.2.6.jar");

        final ResourceSet resourceSet = new ResourceSetImpl();
        JavaClasspath.get(resourceSet).registerSourceOrClassFileFolder(
                URI.createFileURI(srcRoot.getCanonicalPath()));
        JavaClasspath.get(resourceSet).registerClassifierJar(
                URI.createFileURI(jarFile.getCanonicalPath()));

        // @formatter:off
        final Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setMetadataAdapter(new JamoppAdapter(resourceSet))
                .addUrls(srcRoot.toURI().toURL())
                .setScanners(new TypeAnnotationsScanner()));
        // @formatter:on

        // TEST
        final Set<Class<?>> types = reflections.getTypesAnnotatedWith(Label.class);

        // VERIFY
        assertThat(types).hasSize(1);
        assertThat(types.iterator().next().getName()).isEqualTo(A.class.getName());

    }

}
