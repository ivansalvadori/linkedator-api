package br.ufsc.inf.lapesd.linkedator.api.config;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("/alignatorSyncTest.properties")
public class AlignatorSyncTest extends AlignatorTestBase {

    @Test
    public void test() throws Exception {
        Resource sspObject = createResource("http://127.0.0.1/ssp/person/123456");
        Resource sspscObject = createResource("http://127.0.0.1/sspsc/person/123456");

        Assert.assertEquals(200, register("people-ssp-description.json"));
        Assert.assertEquals(200, register("people-sspsc-description.json"));

        Model linked = createLinks(model("person-ssp.ttl"));
        Resource r = linked.createResource( Scenario0.SSP_NS +"Jose");
        Assert.assertTrue(r.hasProperty(OWL2.sameAs, sspObject));

        linked = createLinks(model("person-sspsc.ttl"));
        r = linked.createResource(Scenario0.SSPSC_NS + "Jose");
        Assert.assertTrue(r.hasProperty(OWL2.sameAs, sspscObject));

        linked = createLinks(model("person-ssp.ttl"));
        r = linked.createResource(Scenario0.SSP_NS + "Jose");
        Assert.assertTrue(r.hasProperty(OWL2.sameAs, sspObject));
        Assert.assertTrue(r.hasProperty(OWL2.sameAs, sspscObject));
    }
}
