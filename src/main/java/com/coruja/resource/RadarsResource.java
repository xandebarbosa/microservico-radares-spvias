package com.coruja.resource;

import com.coruja.dto.RadarPageDTO;
import com.coruja.service.RadarsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.LocalTime;

@Path("/radares")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Radares SPVias", description = "Leituras de radares da Concessionária SPVias")
@Slf4j
public class RadarsResource {

    @Inject
    RadarsService radarsService;

    // ═══════════════════════════════════════════════════════════════
    //  GET /radares/filtros  — consulta unificada com filtros opcionais
    // ═══════════════════════════════════════════════════════════════

    /**
     * Endpoint UNIFICADO para buscar radares com filtros opcionais.
     * Exemplos:
     *   GET /radares/filtros?placa=ABC1234&page=0&size=20
     *   GET /radares/filtros?rodovia=SP-300&data=2025-06-06&page=0&size=20
     *   GET /radares/filtros?horaInicial=08:00:00&horaFinal=18:00:00&page=0&size=50
     */
    @GET
    @Path("/busca-local")
    @Operation(summary = "Busca radares com filtros dinâmicos e paginação")
    @APIResponse(responseCode = "200", description = "Página de radares retornada com sucesso")
    public Response buscarComFiltros(
            @Parameter(description = "Placa do veículo (exata)")
            @QueryParam("placa") String placa,

            @Parameter(description = "Nome da praça de pedágio")
            @QueryParam("praca") String praca,

            @Parameter(description = "Sentido da via")
            @QueryParam("sentido") String sentido,

            @Parameter(description = "Data da passagem (ISO: yyyy-MM-dd)")
            @QueryParam("data") String dataStr,

            @Parameter(description = "Hora inicial do intervalo (ISO: HH:mm:ss)")
            @QueryParam("horaInicial") String horaInicialStr,

            @Parameter(description = "Hora final do intervalo (ISO: HH:mm:ss)")
            @QueryParam("horaFinal") String horaFinalStr,

            @Parameter(description = "Número da página (0-indexed)")
            @QueryParam("page") @DefaultValue("0") int page,

            @Parameter(description = "Tamanho da página")
            @QueryParam("size") @DefaultValue("20") int size

    ) {
        // Converte strings ISO para tipos Java (null-safe)
        LocalDate data        =  LocalDate.parse(dataStr);
        LocalTime horaInicial = LocalTime.parse(horaInicialStr);
        LocalTime horaFinal   = LocalTime.parse(horaFinalStr);

        RadarPageDTO resultado = radarsService.buscarComFiltros(
                placa, praca, sentido, data, horaInicial, horaFinal, page, size
        );

        return Response.ok(resultado).build();

    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /radares/placa  — consulta por placa
    // ═══════════════════════════════════════════════════════════════

    @GET
    @Path("/busca-placa")
    @Operation(summary = "Busca passagens paginadas de uma placa")
    @APIResponse(responseCode = "200", description = "Dados da placa retornados")
    @APIResponse(responseCode = "404", description = "Placa não encontrada")
    public Response buscarPorPlaca(
            @Parameter(description = "Placa do veículo (ex: ABC1D23)", required = true)
            @QueryParam("placa") String placa,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        RadarPageDTO resultado = radarsService.buscarPorPlaca(placa, page, size);
        return Response.ok(resultado).build();
    }
}
