package br.ufsc.inf.lapesd.linkedator.api.config;

import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("/linkedatorOnlyTest.properties")
@ContextConfiguration(initializers = {LinkedatorOnlyOntologyUpdateTest.Initializer.class})
@DirtiesContext
public class LinkedatorOnlyOntologyUpdateTest extends LinkedatorApiEndpointTestBase {
    public static class Initializer implements
            ApplicationContextInitializer<ConfigurableApplicationContext> {

        static File ontologyFile;

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            try {
                ontologyFile = Files.createTempFile("", ".ttl").toFile();
                ontologyFile.deleteOnExit();
                setOntologyFilePath(applicationContext, ontologyFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public LinkedatorOnlyOntologyUpdateTest() {
        pathsFmt = "/scenario0/%s";
    }

    @Test(timeout = 5000)
    public void testUpdateOntology() throws Exception {
        Resource sspObject = createResource("http://127.0.0.1/ssp/person/123456");
        Resource otherObject = createResource("http://127.0.0.1/other/person/123456");

        Assert.assertEquals(200, register("people-ssp-description.json"));
        Assert.assertEquals(200, register("people-other-description.json"));

        Model linked = createLinks(model("person-ssp.ttl"));
        Resource r = linked.createResource(Scenario0.SSP_NS + "Jose");
        Assert.assertTrue(r.hasProperty(OWL2.sameAs, sspObject));
        Assert.assertFalse(r.hasProperty(OWL2.sameAs, otherObject));

        /* replace ontology file */
        try (FileOutputStream out = new FileOutputStream(Initializer.ontologyFile);
             InputStream in = getClass().getResourceAsStream("/scenario0/with-alignment.ttl")) {
            IOUtils.copy(in, out);
        }

        for (Thread.sleep(20); true; Thread.sleep(100)) { //fail by timeout
            linked = createLinks(model("person-ssp.ttl"));
            r = linked.createResource(Scenario0.SSP_NS + "Jose");
            Assert.assertTrue(r.hasProperty(OWL2.sameAs, sspObject));
            if (r.hasProperty(OWL2.sameAs, otherObject))
                break; //pass
        }
    }
}
