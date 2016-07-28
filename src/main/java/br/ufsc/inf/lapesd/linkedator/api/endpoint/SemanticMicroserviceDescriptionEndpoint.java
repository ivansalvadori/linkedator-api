package br.ufsc.inf.lapesd.linkedator.api.endpoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.ufsc.inf.lapesd.linkedator.Linkedator;
import br.ufsc.inf.lapesd.linkedator.SemanticMicroserviceDescription;

@Component
@Path("/")
public class SemanticMicroserviceDescriptionEndpoint {

    @Context
    private HttpServletRequest request;

    @Value("${config.ontologyFilePath}")
    private String ontologyFilePath;
    private Linkedator linkedator = null;

    @PostConstruct
    public void init() throws IOException {
        String ontology = new String(Files.readAllBytes(Paths.get(ontologyFilePath)));
        this.linkedator = new Linkedator(ontology);
    }

    @POST
    @Path("microservice/description")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registryMicroserviceDescription(SemanticMicroserviceDescription semanticMicroserviceDescription) {
        String remoteAddr = request.getRemoteAddr();
        semanticMicroserviceDescription.setIpAddress(remoteAddr);
        linkedator.registryDescription(semanticMicroserviceDescription);
        System.out.println(semanticMicroserviceDescription.getMicroserviceFullPath() + " Registered");
        return Response.ok().build();
    }

    @POST
    @Path("createLinks")

    public Response registryMicroserviceDescription(String resourceRepresentation, @QueryParam("verifyLinks") boolean verifyLinks) {
        String representationWithLinks = linkedator.createLinks(resourceRepresentation, verifyLinks);
        return Response.ok(representationWithLinks).build();
    }

}
