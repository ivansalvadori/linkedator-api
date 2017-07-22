package br.ufsc.inf.lapesd.linkedator.api.endpoint;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.*;

import br.ufsc.inf.lapesd.linkedator.ModelBasedLinkedator;
import br.ufsc.inf.lapesd.linkedator.api.utils.FileWatcher;
import br.ufsc.inf.lapesd.linkedator.links.LinkVerifier;
import br.ufsc.inf.lapesd.linkedator.links.NullLinkVerifier;
import br.ufsc.inf.lapesd.linkedator.links.RsClientLinkVerifier;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotNotFoundException;
import org.apache.jena.shared.JenaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import br.ufsc.inf.lapesd.alignator.core.Alignator;
import br.ufsc.inf.lapesd.alignator.core.Alignment;
import br.ufsc.inf.lapesd.alignator.core.entity.loader.ServiceDescription;
import br.ufsc.inf.lapesd.alignator.core.report.EntityLoaderReport;
import br.ufsc.inf.lapesd.alignator.core.report.OntologyManagerReport;
import br.ufsc.inf.lapesd.linkedator.Linkedator;
import br.ufsc.inf.lapesd.linkedator.SemanticMicroserviceDescription;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@Component
@Scope("singleton")
@Path("/")
public class LinkedatorApiEndpoint {
    private static Log log = LogFactory.getLog(LinkedatorApiEndpoint.class);

    @Value("${config.ontologyFilePath:null}")
    private String ontologyFilePath;

    @Value("${config.enableCache}")
    private boolean enableCache ;

    @Value("${config.cacheMaximumSize}")
    private int cacheMaximumSize;

    @Value("${config.cacheExpireAfterAccessSeconds}")
    private int cacheExpireAfterAccessSeconds;

    @Value("${config.ontolyMaxIndividuals}")
    private int ontolyMaxIndividuals;

    @Value("${config.enableAlignator:true}")
    private boolean enableAlignator;

    @Value("${config.enableAlignatorAsync:false}")
    private boolean enableAlignatorAsync;

    @Value("${config.alignatorQueueSize:1}")
    private int alignatorQueueSize;

    private Linkedator linkedator;
    private LinkVerifier linkVerifier;
    private Alignator alignator;
    private ThreadPoolExecutor alignExecutor;
    private FileWatcher ontologyFileWatcher;


    @PostConstruct
    public void init() throws IOException {
        if (enableAlignator)
            initAlignator();
        ontologyFilePath = new File(ontologyFilePath).getAbsolutePath();
        initLinkedator();
    }

    private void initAlignator() {
        Preconditions.checkState(alignatorQueueSize > 0);

        alignator = new Alignator();
        ontologyFilePath = "alignator-merged-ontology.owl";
        this.alignator.getOntologyManager().setOntologyMaxIndividuals(this.ontolyMaxIndividuals);

        alignExecutor = new ThreadPoolExecutor(1, 1, Long.MAX_VALUE, NANOSECONDS,
                new ArrayBlockingQueue<>(alignatorQueueSize));
    }

    private void initLinkedator() {
        linkedator = new ModelBasedLinkedator();
        //throws JenaException, if fails:
        try {
            Model model = RDFDataMgr.loadModel("file://" + ontologyFilePath);
            linkedator.updateOntologies(model);
            log.info(String.format("Initialized Linkedator ontologies with %d triples from %s",
                    model.size(), ontologyFilePath));
        } catch (RiotNotFoundException e) {
            log.error("Ontology file " + ontologyFilePath + "not found, will keep " +
                    "probing for it");
        }
        Cache<String, Boolean> cache = !enableCache ? null :
                CacheBuilder.newBuilder().maximumSize(cacheMaximumSize)
                        .expireAfterAccess(cacheExpireAfterAccessSeconds, TimeUnit.SECONDS).build();
        linkVerifier = new RsClientLinkVerifier(cache);

        if (!enableAlignator) {
            /* if alignator is enabled, reloadOntology() will be called after every alignment */
            try {
                java.nio.file.Path path = Paths.get(ontologyFilePath);
                ontologyFileWatcher = new FileWatcher(path, p -> reloadOntology());
                ontologyFileWatcher.start();
            } catch (IOException e) {
                String msg = "Could not setup watch on ontology file " + ontologyFilePath;
                throw new RuntimeException(msg, e);
            }
        }
    }

    private void reloadOntology() {
        String uri = "file://" + ontologyFilePath;
        if (new File(ontologyFilePath).length() == 0) {
            log.info("Ontology file at " + uri + " truncated. " +
                    "Ignored, waiting for further notifications");
        } else {
            try {
                Model model = RDFDataMgr.loadModel(uri);
                linkedator.updateOntologies(model);
                log.info(String.format("Reloaded ontology from %s with %d triples",
                        uri, model.size()));
            } catch (JenaException e) {
                log.error("Bad ontology at " + uri, e);
            }
        }
    }

    @PreDestroy
    public void destroy() throws Exception {
        if (enableAlignator) {
            alignExecutor.shutdown();
            alignExecutor.getQueue().forEach(alignExecutor::remove);
            alignExecutor.purge();
            alignExecutor.awaitTermination(30, SECONDS);
        }
        if (ontologyFileWatcher != null) {
            ontologyFileWatcher.cancel();
            ontologyFileWatcher.join(30 * 1000);
        }
    }

    @POST
    @Path("microservice/description")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registerMicroserviceDescription(SemanticMicroserviceDescription smd,
                                                    @Context HttpServletRequest request)
            throws IOException {
        String remoteAddr = request.getRemoteAddr();
        smd.setIpAddress(remoteAddr);

        String ontologyBase64 = smd.getOntologyBase64();
        String ontology = new String(Base64.getDecoder().decode(ontologyBase64.getBytes()));
        String json = new Gson().toJson(smd);
        ServiceDescription serviceDescription = new Gson().fromJson(json, ServiceDescription.class);
        if (enableAlignator)
            alignator.registerService(serviceDescription, ontology);

        linkedator.register(smd);

        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, IOUtils.toInputStream(ontology, "UTF-8"), Lang.RDFXML);
        linkedator.addToOntologies(model);

        return Response.ok().build();
    }

    @POST
    @Path("createLinks")
    public Response createLinks(Model model, @QueryParam("verifyLinks") boolean verifyLinks,
                                @Context HttpHeaders headers) {
        String prefix = String.format("createLinks(%d triples, verifyLinks=%b",
                model.size(), verifyLinks);
        log.trace(prefix + " - IN");

        Stopwatch watch = Stopwatch.createStarted();
        Exception linkedatorException = null;
        try {
            linkedator.createLinks(model, verifyLinks ? linkVerifier : new NullLinkVerifier());
        } catch (Exception e) {
            log.error(prefix + " - linkedator.createLinks() failed", e);
            linkedatorException = e;
        }
        Response.ResponseBuilder builder = Response.ok(model, headers.getMediaType())
                .header("X-Linkedator-Time", watch.elapsed(MILLISECONDS));
        if (linkedatorException != null)
            builder = builder.header("X-Linkedator-Error", linkedatorException.getMessage());

        if (enableAlignator)
            builder = feedAlignator(model, builder);

        log.trace(prefix + " - OUT");
        return builder.build();
    }

    private Response.ResponseBuilder feedAlignator(Model model, Response.ResponseBuilder builder) {
        Throwable alignatorException = null;
        Stopwatch watch = Stopwatch.createStarted();
        try {
            align(model);
        } catch (ExecutionException e) {
            alignatorException = e.getCause();
        } catch (Exception e) {
            alignatorException = e;
        }
        if (!enableAlignatorAsync)
            builder = builder.header("X-Alignator-Time", watch.elapsed(MILLISECONDS));
        if (alignatorException != null)
            builder = builder.header("X-Alignator-Error", alignatorException.getMessage());
        return builder;
    }

    /**
     * Either calls {@link Alignator}.loadEntitiesAndAlignOntologies() or queues the
     * representation for a later call by a worker thread.
     */
    private void align(Model model) throws InterruptedException, ExecutionException {
        Preconditions.checkState(enableAlignator);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, model, Lang.JSONLD);
        String representation;
        try {
            representation = out.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        try {
            Future<Void> future = alignExecutor.submit(new AlignatorTask(representation));
            if (!enableAlignatorAsync)
                future.get();
        } catch (RejectedExecutionException ignored) { }
    }

    @GET
    @Path("ontology")
    public Model loadOntology() {
        return linkedator.getOntologies();
    }

    @GET
    @Path("report/entityLoader")
    @Produces("text/plain")
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
    @Produces("text/plain")
    public Response loadReportAlignments() {

        StringBuilder response = new StringBuilder("executionId,property1,property2,strength,ontologyMatcherTime,alignatorTime\n");
        List<EntityLoaderReport> reportList = this.alignator.getEntityLoaderReportList();
        for (EntityLoaderReport report : reportList) {
            List<Alignment> alignments = report.getAlignments();
            for (Alignment alignment : alignments) {
                response.append(String.format("%s,%s,%s,%s,%s,%s\n", report.getExecutionId(), alignment.getUri1(), alignment.getUri2(), alignment.getStrength(), report.getMatcherElapsedTime(), report.getAlignatorElapsedTime()));
            }
        }

        return Response.ok(response.toString()).build();
    }

    @GET
    @Path("report/ontologyManager")
    @Produces("text/plain")
    public Response loadReportOntologyManager() {

        StringBuilder response = new StringBuilder("executionId,ontologyBaseUri,numberOfIndividuals,numberOfCharsOntologyModel\n");
        List<OntologyManagerReport> ontologyManagerReportList = this.alignator.getOntologyManagerReportList();
        for (OntologyManagerReport report : ontologyManagerReportList) {
            response.append(String.format("%s,%s,%s,%s\n", report.getExecutionId(), report.getOntologyBaseUri(), report.getNumberOfIndividuals(), report.getNumberOfCharsOntologyModel()));
        }

        return Response.ok(response.toString()).build();
    }

    private class AlignatorTask implements Callable<Void> {
        private final @Nonnull String representation;

        private AlignatorTask(@Nonnull String representation) {
            this.representation = representation;
        }

        @Override
        public Void call() throws Exception {
            try {
                alignator.loadEntitiesAndAlignOntologies(representation);
                reloadOntology();
            } catch (Exception e) {
                String base64 = Base64.getEncoder()
                        .encodeToString(representation.getBytes(StandardCharsets.UTF_8));
                log.error("AlignerThread.run - " + "Exception on alignator.loadEntitiesAndAlignOntologies(). " +
                        "representation base64: " + base64, e);
                throw e;
            }
            return  null;
        }
    }
}
