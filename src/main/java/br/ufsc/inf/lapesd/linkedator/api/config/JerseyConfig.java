package br.ufsc.inf.lapesd.linkedator.api.config;

import javax.ws.rs.ApplicationPath;

import br.ufsc.inf.lapesd.ld_jaxrs.jena.JenaProviders;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spring.scope.RequestContextFilter;
import org.springframework.stereotype.Component;

@Component
@ApplicationPath("/linkedator-api")
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        this.register(RequestContextFilter.class);
        JenaProviders.getProviders().forEach(this::register);
        this.packages("br.ufsc.inf.lapesd.linkedator.api.endpoint");
        this.register(CorsInterceptor.class);
    }
}