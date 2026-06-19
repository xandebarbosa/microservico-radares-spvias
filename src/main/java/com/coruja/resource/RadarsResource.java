package com.coruja.resource;

import com.coruja.dto.PracaDTO;
import com.coruja.dto.RadarLocationDTO;
import com.coruja.dto.RadarPageDTO;
import com.coruja.dto.RadarsDTO;
import com.coruja.entity.Radars;
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
import java.util.List;

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
        LocalDate data        = parseDate(dataStr);
        LocalTime horaInicial = parseTime(horaInicialStr);
        LocalTime horaFinal   = parseTime(horaFinalStr);

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

    // ═══════════════════════════════════════════════════════════════
    //  GET /radares  — lista todos (sem filtros)
    // ═══════════════════════════════════════════════════════════════
    @GET
    @Operation(summary = "Lista todos os radares paginados")
    public Response getAllRadars(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        RadarPageDTO resultado = radarsService.buscarComFiltros(
                null, null, null, null, null, null, page, size
        );

        return Response.ok(resultado).build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  POST /radares/salvar  — ingestão manual
    // ═══════════════════════════════════════════════════════════════
    @POST
    @Path("/salvar")
    @Operation(summary = "Salva leituras de radares e publica no RabbitMQ")
    @APIResponse(responseCode = "201", description = "Radares salvos com sucesso")
    public Response salvarRadares(List<Radars> radars) {
        radarsService.salvarRadares(radars);
        return Response.status(Response.Status.CREATED)
                .entity("Radares salvos com sucesso!")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /radares/rodovias
    // ═══════════════════════════════════════════════════════════════
    @GET
    @Path("/rodovias")
    @Operation(summary = "Lista as pracas disponíveis para filtro")
    public Response listarPracas() {
        List<PracaDTO> pracas = radarsService.listarPracas();

        log.info("✅ [SPVias] Retornando {} rodovia", pracas.size());

        return Response.ok(pracas).build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /radares/ultimos
    // ═══════════════════════════════════════════════════════════════
    @GET
    @Path("/ultimos")
    @Operation(summary = "Busca os radares mais recente processados")
    public Response buscarUltimos(@QueryParam("limite") @DefaultValue("5") int limite) {
        List<RadarsDTO> ultimos = radarsService.buscarUltimos(limite);
        if (ultimos == null || ultimos.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Nenhum registro encontrado.")
                    .build();
        }
        return Response.ok(ultimos).build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /radares/geo-search  — Busca por Raio
    // ═══════════════════════════════════════════════════════════════
    @GET
    @Path("/geo-search")
    @Operation(summary = "Busca radares por geolocalização (raio em metros)")
    @APIResponse(responseCode = "200", description = "Radares encontrados dentro do raio")
    public Response buscarPorGeolocalizacao(
            @Parameter(description = "Latitude do centro da busca", required = true)
            @QueryParam("latitude") Double latitude,

            @Parameter(description = "Longitude do centro da busca", required = true)
            @QueryParam("longitude") Double longitude,

            @Parameter(description = "Raio de busca em metros", required = true)
            @QueryParam("raio") Double raio,

            @Parameter(description = "Data da passagem (ISO: yyyy-MM-dd)", required = true)
            @QueryParam("data") String data,

            @Parameter(description = "Número da página")
            @QueryParam("page") @DefaultValue("0") int page,

            @Parameter(description = "Tamanho da página")
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        //Validação de segurança básica para o FrontEnd
        if (latitude == null || longitude == null || raio == null || data == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Latitude, longitude, raio e data são obrigatórios para a busca geográfica.")
                    .build();
        }

        log.info("📡 [SPVias] Busca Geo: Lat: {}, Lng: {}, Raio: {}m, Data: {}", latitude, longitude, raio, data);

        RadarPageDTO resultado = radarsService.buscaGeografica(latitude, longitude, raio, data, page, size);
        return Response.ok(resultado).build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /radares/all-locations  — Pinos do Mapa
    // ═══════════════════════════════════════════════════════════════
    @GET
    @Path("/all-locations")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retorna todas as localizações fixas de radares para popular o mapa.")
    public Response getAllLocations() {
        log.info("🚨 [SPVias-MAPA] SUCESSO! A requisição do BFF conseguiu chegar na Rondon!");

        List<RadarLocationDTO> locations = radarsService.getAllLocations();
        log.info("🚨 [SPVias-MAPA] Devolvendo {} coordenadas para o BFF desenhar.", locations.size());

        return Response.ok(locations).build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private LocalDate parseDate(String str) {
        if (str == null || str.isBlank()) return null;
        try { return LocalDate.parse(str); }
        catch (Exception e) { return null; }
    }

    private LocalTime parseTime(String str) {
        if (str == null || str.isBlank()) return null;
        try { return LocalTime.parse(str); }
        catch (Exception e) { return null; }
    }
}
