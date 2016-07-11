package br.ufsc.inf.lapesd.linkedator.api.endpoint;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import br.ufsc.inf.lapesd.linkedator.SemanticMicroserviceDescription;

@Path("microservice/description")
public class SemanticMicroserviceDescriptionEndpoint {

    @POST
    public Response registryMicroserviceDescription(SemanticMicroserviceDescription semanticMicroserviceDescription) {
        return null;
    }

}
