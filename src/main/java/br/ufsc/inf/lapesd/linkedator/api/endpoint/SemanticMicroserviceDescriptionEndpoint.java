package br.ufsc.inf.lapesd.linkedator.api.endpoint;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import br.ufsc.inf.lapesd.alignator.core.Alignator;
import br.ufsc.inf.lapesd.alignator.core.Alignment;
import br.ufsc.inf.lapesd.alignator.core.entity.loader.ServiceDescription;
import br.ufsc.inf.lapesd.alignator.core.report.EntityLoaderReport;
import br.ufsc.inf.lapesd.alignator.core.report.OntologyManagerReport;
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

        try {
            ontology = new String(Files.readAllBytes(Paths.get("alignator-merged-ontology.owl")));

        } catch (NoSuchFileException e) {
            System.out.println("alignator-merged-ontology.owl does not exist");
        } finally {
            this.linkedator = new Linkedator(ontology);
            this.linkedator.enableCache(enableCache);
            this.linkedator.setCacheConfiguration(cacheMaximumSize, cacheExpireAfterAccessSeconds);
            this.alignator = new Alignator();
        }
    }

    @POST
    @Path("microservice/description")
    @Consumes(MediaType.APPLICATION_JSON)
    @SuppressWarnings("finally")
    public Response registryMicroserviceDescription(SemanticMicroserviceDescription semanticMicroserviceDescription) {
        String remoteAddr = request.getRemoteAddr();
        semanticMicroserviceDescription.setIpAddress(remoteAddr);

        String ontologyBase64 = semanticMicroserviceDescription.getOntologyBase64();
        String ontology = new String(Base64.getDecoder().decode(ontologyBase64.getBytes()));

        alignator.registerService(new Gson().fromJson(semanticMicroserviceDescription.toString(), ServiceDescription.class), ontology);

        this.semanticMicroserviceDescriptions.add(semanticMicroserviceDescription);

        try {
            String mergedOntology = new String(Files.readAllBytes(Paths.get("alignator-merged-ontology.owl")));
            this.linkedator = new Linkedator(mergedOntology);
            this.linkedator.enableCache(enableCache);
            this.linkedator.setCacheConfiguration(cacheMaximumSize, cacheExpireAfterAccessSeconds);

            for (SemanticMicroserviceDescription registeredSemanticMicroserviceDescription : semanticMicroserviceDescriptions) {
                this.linkedator.registryMicroserviceDescription(registeredSemanticMicroserviceDescription);
            }

        } catch (IOException e) {
            this.linkedator.registryMicroserviceDescription(semanticMicroserviceDescription);
            this.linkedator.updateOntology(ontology);
        } finally {
            return Response.ok().build();
        }

    }

    @POST
    @Path("createLinks")
    @SuppressWarnings("finally")
    public Response createLinks(String resourceRepresentation, @QueryParam("verifyLinks") boolean verifyLinks) {

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
            align(resourceRepresentation);
            return Response.ok(representationWithLinks).build();
        }
    }

    @Async
    private void align(String resourceRepresentation) {
        alignator.loadEntitiesAndAlignOntologies(resourceRepresentation);
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

    @GET
    @Path("report/entityLoader")
    @Produces("text/csv")
    public Response loadReport() {

        StringBuilder response = new StringBuilder("executionId,numberOfLoadedEntities,numberOfCharsLoadedEntities\n");
        List<EntityLoaderReport> reportList = this.alignator.getEntityLoaderReportList();
        for (EntityLoaderReport report : reportList) {
            response.append(String.format("%s,%s,%s\n", report.getExecutionId(), report.getNumberOfLoadedEntities(), report.getNumberOfCharsLoadedEntities()));
        }

        return Response.ok(response.toString()).build();
    }

    @GET
    @Path("report/entityLoader/alignments")
    @Produces("text/csv")
    public Response loadReportAlignments() {

        StringBuilder response = new StringBuilder("executionId,numberOfLoadedEntities,numberOfCharsLoadedEntities\n");
        List<EntityLoaderReport> reportList = this.alignator.getEntityLoaderReportList();
        for (EntityLoaderReport report : reportList) {
            List<Alignment> alignments = report.getAlignments();
            for (Alignment alignment : alignments) {
                response.append(String.format("%s,%s,%s,%s\n", report.getExecutionId(), alignment.getUri1(), alignment.getUri2(), alignment.getStrength()));
            }
        }

        return Response.ok(response.toString()).build();
    }

    @GET
    @Path("report/ontologyManager")
    @Produces("text/csv")
    public Response loadReportOntologyManager() {

        StringBuilder response = new StringBuilder("executionId,ontologyBaseUri,numberOfIndividuals,numberOfCharsOntologyModel\n");
        List<OntologyManagerReport> ontologyManagerReportList = this.alignator.getOntologyManagerReportList();
        for (OntologyManagerReport report : ontologyManagerReportList) {
            response.append(String.format("%s,%s,%s,%s\n", report.getExecutionId(), report.getOntologyBaseUri(), report.getNumberOfIndividuals(), report.getNumberOfCharsOntologyModel()));
        }

        return Response.ok(response.toString()).build();
    }
}
