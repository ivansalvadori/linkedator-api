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
@TestPropertySource("/alignatorAsyncTest.properties")
public class AlignatorAsyncTest  extends AlignatorTestBase {

    @Test(timeout = 5000)
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

        for (Thread.sleep(100); true; Thread.sleep(100)) { //wait for alignator
            linked = createLinks(model("person-ssp.ttl"));
            r = linked.createResource(Scenario0.SSP_NS + "Jose");
            Assert.assertTrue(r.hasProperty(OWL2.sameAs, sspObject));
            if (r.hasProperty(OWL2.sameAs, sspscObject))
                break; //pass
        }
    }
}
