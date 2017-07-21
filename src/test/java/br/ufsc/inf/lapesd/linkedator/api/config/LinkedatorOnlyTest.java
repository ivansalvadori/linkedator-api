package br.ufsc.inf.lapesd.linkedator.api.config;

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
import java.io.IOException;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("/linkedatorOnlyTest.properties")
@ContextConfiguration(initializers = LinkedatorOnlyTest.Initializer.class)
@DirtiesContext
public class LinkedatorOnlyTest extends LinkedatorApiEndpointTestBase {
    public static class Initializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            try {
                File file = ResourceExtractor.extract(getClass().getResourceAsStream(
                        "/scenario0/alignment.ttl"), ".ttl");
                file.deleteOnExit();
                setOntologyFilePath(applicationContext, file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public LinkedatorOnlyTest() {
        pathsFmt = "/scenario0/%s";
    }

    @Test
    public void testCreateLinks() throws Exception {
        Assert.assertEquals(200, register("people-ssp-description.json"));
        Assert.assertEquals(200, register("people-other-description.json"));

        Model model = createLinks(model("person-ssp.ttl"));
        Resource r = model.createResource( Scenario0.SSP_NS +"Jose");
        Assert.assertTrue(r.hasProperty(OWL2.sameAs, model.createResource(
                "http://127.0.0.1/ssp/person/123456")));
        Assert.assertTrue(r.hasProperty(OWL2.sameAs, model.createResource(
                "http://127.0.0.1/other/person/123456")));
    }
}