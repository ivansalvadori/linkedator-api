package br.ufsc.inf.lapesd.linkedator.api.config;

import br.ufsc.inf.lapesd.ld_jaxrs.jena.JenaProviders;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

import javax.annotation.Nonnull;
import javax.ws.rs.client.Entity;
import java.io.File;
import java.io.IOException;

public class LinkedatorApiEndpointTestBase {
    @LocalServerPort
    private int port;

    protected String pathsFmt = null;

    protected static void setOntologyFilePath(ConfigurableApplicationContext applicationContext, File file) {
        String path = file.getAbsolutePath();
        path = path.replace(":", "\\:");
        path = path.replace("=", "\\=");
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext,
                "config.ontologyFilePath=" + path);
    }

    private JerseyWebTarget target(String path) {
        JerseyClient client = JerseyClientBuilder.createClient();
        JenaProviders.getProviders().forEach(client::register);
        String uri = "http://localhost:" + port + "/linkedator-api"
                + (!path.startsWith("/") ? "/" : "") + path;
        return client.target(uri);
    }

    @Nonnull
    protected Model model(String filename) throws IOException {
        String path = String.format(pathsFmt, filename);
        Lang lang = RDFLanguages.filenameToLang(path);
        return ResourceExtractor.model(getClass().getResourceAsStream(path), lang);
    }

    protected Model createLinks(@Nonnull Model model) throws IOException {
        return target("/createLinks?verifyLinks=false")
                .request("application/ld+json")
                .post(Entity.entity(model, "application/ld+json"), Model.class);
    }

    protected int register(@Nonnull String filename) throws IOException {
        return target("/microservice/description")
                    .request()
                    .post(Entity.json(ResourceExtractor.toString(getClass()
                            .getResourceAsStream(String.format(pathsFmt, filename)))))
                    .getStatus();
    }
}
