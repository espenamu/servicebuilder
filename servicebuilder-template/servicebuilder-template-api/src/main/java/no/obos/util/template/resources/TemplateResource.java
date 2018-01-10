package no.obos.util.template.resources;

import io.swagger.annotations.Api;
import no.obos.util.template.dto.TemplateDto;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api
@Path("examples")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface TemplateResource {
    @GET
    List<TemplateDto> getAllTemplates();

    @GET
    @Path("{id}")
    TemplateDto getTemplate(@PathParam("id") int id);

    @POST
    int createTemplate(TemplateDto payload);

    @PUT
    @Path("{id}")
    void updateTemplate(@PathParam("id") int id, TemplateDto payload);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") int id);
}