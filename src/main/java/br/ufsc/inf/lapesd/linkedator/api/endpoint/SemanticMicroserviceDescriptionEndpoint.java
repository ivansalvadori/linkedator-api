package br.ufsc.inf.lapesd.linkedator.api.endpoint;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.jena.ontology.OntModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import br.ufsc.inf.lapesd.alignator.core.Alignator;
import br.ufsc.inf.lapesd.alignator.core.entity.loader.ServiceDescription;
import br.ufsc.inf.lapesd.linkedator.Linkedator;
import br.ufsc.inf.lapesd.linkedator.SemanticMicroserviceDescription;

@Component
@Path("/")
public class SemanticMicroserviceDescriptionEndpoint {

    @Context
    private HttpServletRequest request;

    @Value("${config.ontologyFilePath}")
    private String ontologyFilePath;

    @Value("${config.enableCache}")
    private boolean enableCache;

    @Value("${config.cacheMaximumSize}")
    private int cacheMaximumSize;

    @Value("${config.cacheExpireAfterAccessSeconds}")
    private int cacheExpireAfterAccessSeconds;

    private List<SemanticMicroserviceDescription> semanticMicroserviceDescriptions = new ArrayList<>();

    private Linkedator linkedator = null;

    private Alignator alignator = null;

    @PostConstruct
    public void init() throws IOException {
        String ontology = new String(Files.readAllBytes(Paths.get(ontologyFilePath)));
        this.linkedator = new Linkedator(ontology);
        this.linkedator.enableCache(enableCache);
        this.linkedator.setCacheConfiguration(cacheMaximumSize, cacheExpireAfterAccessSeconds);
        alignator = new Alignator();

    }

    @POST
    @Path("microservice/description")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registryMicroserviceDescription(SemanticMicroserviceDescription semanticMicroserviceDescription) {
        String remoteAddr = request.getRemoteAddr();
        semanticMicroserviceDescription.setIpAddress(remoteAddr);
        linkedator.registryMicroserviceDescription(semanticMicroserviceDescription);

        String ontologyBase64 = semanticMicroserviceDescription.getOntologyBase64();
        String ontology = new String(Base64.getDecoder().decode(ontologyBase64.getBytes()));
        alignator.registerService(new Gson().fromJson(semanticMicroserviceDescription.toString(), ServiceDescription.class), ontology);

        this.semanticMicroserviceDescriptions.add(semanticMicroserviceDescription);

        return Response.ok().build();
    }

    @POST
    @Path("createLinks")
    @SuppressWarnings("finally")
    public Response createLins(String resourceRepresentation, @QueryParam("verifyLinks") boolean verifyLinks) {

        try {
            String ontology = new String(Files.readAllBytes(Paths.get("alignator-merged-ontology.owl")));
            this.linkedator = new Linkedator(ontology);
            this.linkedator.enableCache(enableCache);
            this.linkedator.setCacheConfiguration(cacheMaximumSize, cacheExpireAfterAccessSeconds);

            for (SemanticMicroserviceDescription semanticMicroserviceDescription : semanticMicroserviceDescriptions) {
                this.linkedator.registryMicroserviceDescription(semanticMicroserviceDescription);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            final String representationWithLinks = linkedator.createLinks(resourceRepresentation, verifyLinks);
            alignator.loadEntitiesAndAlignOntologies(representationWithLinks);
            return Response.ok(representationWithLinks).build();
        }
    }

    @GET
    @Path("ontology")
    @Produces(MediaType.APPLICATION_XML)
    public Response loadOntology() {

        OntModel ontoModel = this.linkedator.getOntologyReader().getOntoModel();

        String ontology = "";
        try (StringWriter out = new StringWriter()) {
            ontoModel.write(out, "RDF/XML");
            ontology = out.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return Response.ok(ontology).build();

    }
}
