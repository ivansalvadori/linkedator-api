package br.ufsc.inf.lapesd.linkedator.api.config;

import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;

@ContextConfiguration(initializers = {AlignatorTestBase.Initializer.class})
@DirtiesContext
public class AlignatorTestBase extends LinkedatorApiEndpointTestBase {
    public static class Initializer implements
            ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            try {
                File file = Files.createTempFile("", ".ttl").toFile();
                file.deleteOnExit();
                setOntologyFilePath(applicationContext, file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public AlignatorTestBase() {
        pathsFmt = "/scenario0/%s";
    }
}
