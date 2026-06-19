package com.coruja.service;

import com.coruja.dto.*;
import com.coruja.entity.Radars;
import com.coruja.messaging.RadarMqPublisher;
import com.coruja.repository.RadarsRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import io.quarkus.mongodb.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class RadarsService {

    private static final Logger LOG = Logger.getLogger(RadarsService.class);

    // Padrão de data gravado no MongoDB (ex: "07/02/2026")
    private static final DateTimeFormatter MONGO_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            MONGO_DATE_FMT,
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm")
    );

    @Inject
    RadarsRepository radarsRepository;

    @Inject
    RadarMqPublisher radarMqPublisher;

    @Inject
    MongoClient mongoClient;

    @Inject
    MapaCoordenadasService mapaCoordenadas;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "Veiculos")
    String databaseName;

    /**
     * Busca paginada com todos os filtros opcionais.
     * Equivalente ao buscarComFiltros() do Spring com JPA Specification.
     *
     * @param placa        Placa exata do veículo
     * @param local        Local (busca parcial, case-insensitive)
     * @param sentido      Sentido da via
     * @param data         Data da passagem (LocalDate → convertido para String no filtro)
     * @param horaInicial  Hora inicial do intervalo
     * @param horaFinal    Hora final do intervalo
     * @param page         Número da página (0-indexed)
     * @param size         Tamanho da página
     */
    public RadarPageDTO buscarComFiltros(
            String placa,
            String local,
            String sentido,
            LocalDate data,
            LocalTime horaInicial,
            LocalTime horaFinal,
            int page,
            int size
    ) {
        // Monta o filtro nativo do MongoDB ordenado para aproveitar os índices temporais
        Document filter = buildFilter(placa, local, sentido, data, horaInicial, horaFinal);

        // Conta o total de elementos que dão "match" com o filtro (Super rápido)
        long total = radarsRepository.countWithFilter(filter);

        log.info("🔍 Total encontrado no Mongo para o filtro {}: {}", filter.toJson(), total);

        // 🚀 Ordenação por DATA e HORA para evitar o Collection Scan pelo _id
        Document sortFilter = new Document("DATA", -1).append("HORA", -1);

        //Busca os dados paginados usando a ordenação explícita
        List<RadarsDTO> content = radarsRepository.findWithFilterAndSort(filter, sortFilter, page, size)
                .stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());

        // Calcula o total de páginas
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);

        // Monta o objeto de paginação idêntico ao do Spring
        PageMetadata meta = new PageMetadata(page, size, total, totalPages);

        return new RadarPageDTO(content, meta);
    }

    /**
     * Monta o documento de filtro BSON dinamicamente.
     * Apenas os parâmetros não-nulos são adicionados.
     */
    private Document buildFilter(String placa, String local, String sentido,
                                 LocalDate data, LocalTime horaInicial, LocalTime horaFinal) {

        Document filter = new Document(); // O BSON preserva a ordem de inserção

        // 1. OTIMIZAÇÃO: Filtros temporais PRIMEIRO.
        // Força o MongoDB a usar a "via expressa" dos índices de DATA/HORA existentes.
        if (data != null) {
            filter.append("DATA", data.format(MONGO_DATE_FMT));
        }

        if (horaInicial != null || horaFinal != null) {
            Document horaFilter = new Document();
            if (horaInicial != null) horaFilter.append("$gte", horaInicial.toString());
            if (horaFinal != null) horaFilter.append("$lte", horaFinal.toString());
            filter.append("HORA", horaFilter);
        }

        // 2. FILTROS EXATOS DEPOIS.
        // O Mongo aplicará esses filtros em memória apenas na pequena parcela de dados daquela hora.
        if (placa != null && !placa.isBlank()) {
            String regexPlaca = ".*" + Pattern.quote(placa.toUpperCase().trim()) + ".*";
            filter.append("PLACA", new Document("$regex", regexPlaca).append("$options", "i"));
        }

        if (local != null && !local.isBlank()) {
            String LocalDb = local.trim().toLowerCase();
            filter.append("LOCAL", LocalDb);
        }

        if (sentido != null && !sentido.isBlank()) {
            // Formata no Java para evitar o regex custoso no banco
            String s = sentido.trim().toLowerCase();
            String sentidoFormatado = s.substring(0, 1).toUpperCase() + s.substring(1);
            filter.append("SENTIDO", sentidoFormatado);
        }

        log.info("🔎 Filtro JSON ordenado para o Mongo: {}", filter.toJson());

        return filter;
    }

    /**
     * Consulta por placa.
     */
    public RadarPageDTO buscarPorPlaca(String placa, int page, int size) {
        if (placa == null || placa.isBlank()) {
            PageMetadata meta = new PageMetadata(page, size, 0, 0);
            return new RadarPageDTO(new java.util.ArrayList<>(), meta);
        }

        String placaNorm = placa.toUpperCase().trim();

        // 💡 CORREÇÃO 1: Troca de Regex (.*PLACA.*) por correspondência EXATA.
        // Isso faz o MongoDB saltar direto para os registros no índice, sem ler a tabela inteira!
        Document filter = new Document("PLACA", placaNorm);

        // Conta o total de registros usando o índice composto de forma ultra rápida
        long total = radarsRepository.countWithFilter(filter);

        if (total == 0) {
            PageMetadata meta = new PageMetadata(page, size, 0, 0);
            return new RadarPageDTO(new java.util.ArrayList<>(), meta);
        }

        // 💡 CORREÇÃO 2: Garantir a ordenação correta nativa do banco.
        // Criamos o objeto de sort batendo exatamente com as colunas do seu índice do Compass.
        Document sortFilter = new Document("DATA", -1).append("HORA", -1);

        // Ajuste o seu repositório para receber o sort (ou passe dentro do seu findWithFilter adaptado)
        // Se o seu método findWithFilter atual aceitar apenas filtros de paginação, passe o sort para ele.
        // Exemplo ideal de chamada garantindo o índice:
        List<RadarsDTO> content = radarsRepository.findWithFilterAndSort(filter, sortFilter, page, size)
                .stream()
                .map(this::converterParaDTO)
                .collect(java.util.stream.Collectors.toList());

        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        PageMetadata meta = new PageMetadata(page, size, total, totalPages);

        return new RadarPageDTO(content, meta);
    }

    /**
     * Persiste uma lista de radares e publica cada um no RabbitMQ.
     * Mantido para compatibilidade com o endpoint POST /radares/salvar.
     */
    public void salvarRadares(List<Radars> radars) {
        if (radars == null || radars.isEmpty()) return;

        radarsRepository.persist(radars);
        log.info("{} registros persistidos no MongoDB.", radars.size());

        radars.forEach(radarMqPublisher::publicar);
    }

    /**
     * Retorna a rodovia fixa da concessionária, espelhando o contrato de tabela do SPVias.
     */
    public List<PracaDTO> listarPracas() {
        return List.of(new PracaDTO( 1L, ""));
    }

    /**
     * Busca os útimos registros no banco e os converte para DTOs.
     */
    public List<RadarsDTO> buscarUltimos(int limite) {
        //Document sortDocument = new Document("DATA", -1).append("HORA", -1);

        // Ordena pelo _id decrescente.
        // Como o ObjectId do MongoDB possui o timestamp de criação embutido,
        // o maior _id será SEMPRE o radar que acabou de passar na rodovia!
        Document sortDocument = new Document("_id", -1);

        return radarsRepository.mongoCollection()
                .find()
                .sort(sortDocument)
                .limit(limite)
                .into(new ArrayList<>())
                .stream()
                .map(this::converterParaDTO)  // método já existe
                .collect(Collectors.toList());
    }

    private MongoCollection<Document> getCollection() {
        MongoDatabase db = mongoClient.getDatabase(databaseName);
        return db.getCollection("SPVias");
    }

    private RadarsDTO converterParaDTO(Radars entity) {
        return RadarsDTO.builder()
                .id(Math.abs(UUID.randomUUID().getMostSignificantBits()))
                .data(parseData(entity.getData()))
                .hora(parseHora(entity.getHora()))
                .placa(entity.getPlaca())
                .praca(entity.getLocal())
                .sentido(transformarSentido(entity.getSentido()))
                .concessionaria("SPVias")
                .build();
    }

    private String transformarSentido(String sigla) {
        if (sigla == null || sigla.trim().isEmpty()) {
            return "Desconhecido";
        }

        return switch (sigla.trim().toUpperCase()) {
            case "L" -> "Leste";
            case "O" -> "Oeste";
            case "N" -> "Norte";
            case "S" -> "Sul";
            default -> sigla; // Retorna o valor original caso venha algo fora do padrão
        };
    }

    /**
     * Retorna todas as localizações fixas de radares para popular o mapa.
     * Segue o padrão de retorno do microserviço Cart, mas consome o JSON local.
     */
    public List<RadarLocationDTO> getAllLocations() {
        LOG.info("🗺️ [SPVias] Gerando localizações fixas a partir do mapeamento JSON");

        // O mapaCoordenadasService já carrega o localizacoes-rondon.json no startup
        return mapaCoordenadas.getAllCoordenadas().entrySet().stream()
                .map( entry -> {
                    RadarLocationDTO dto = new RadarLocationDTO();

                    // 🔥 Gera um número Long único baseado no nome do KM para satisfazer o BFF
                    long numericId = Math.abs((long) entry.getKey().hashCode());
                    dto.setId(numericId);

                    dto.setConcessionaria("SPVias");
                    dto.setPraca(entry.getKey());
                    dto.setLatitude(entry.getValue().latitude);
                    dto.setLongitude(entry.getValue().longitude);
                    dto.setSentido("Ambos");
                    return dto;
                }).collect(Collectors.toList());
    }


    /**
     * 2. BUSCA GEOGRÁFICA (Raio / Geo-Search)
     * Contorna a falta de colunas geográficas no MongoDB filtrando os KMs em memória.
     */
    public RadarPageDTO buscaGeografica(Double latCentro, Double lngCentro, Double raioMetros, String dataBusca, int page, int size) {
        // 1. Encontrar quais KMs fixos estão dentro do raio solicitado
        List<String> kmsNoRaio  = new ArrayList<>();

        for (Map.Entry<String, MapaCoordenadasService.Coordenada> entry : mapaCoordenadas.getAllCoordenadas().entrySet()) {
            double distancia = calcularDistanciaHaversine(
                    latCentro, lngCentro,
                    entry.getValue().latitude, entry.getValue().longitude
            );

            if (distancia <= raioMetros) {
                kmsNoRaio.add(entry.getKey());
            }
        }
        if (kmsNoRaio.isEmpty()) {
            return new RadarPageDTO();
        }

        // 2. Consultar o MongoDB
        String query = "data = ?1 and local in ?2";
        PanacheQuery<Radars> panacheQuery = radarsRepository.find(query, dataBusca, kmsNoRaio);

        panacheQuery.page(page, size);
        List<Radars> radaresEncontrados = panacheQuery.list();

        // 3. Mapear para RadarsDTO
        List<RadarsDTO> content = radaresEncontrados.stream().map(radar -> {
            RadarsDTO dto = new RadarsDTO();

            // CONVERSÃO SEGURA PARA LONG: Transforma o hash alfanumérico num número positivo
            if (radar.getId() != null) {
                long numericId = Math.abs((long) radar.getId().toHexString().hashCode());
                dto.setId(numericId);
            }

            // Conversão de data e hora
            try {
                if (radar.getData() != null && !radar.getData().isBlank()) {
                    dto.setData(LocalDate.parse(radar.getData()));
                }
                if (radar.getHora() != null && !radar.getHora().isBlank()) {
                    dto.setHora(LocalTime.parse(radar.getHora()));
                }
            } catch (Exception e) {
                // Ignora falhas de formatação de data
            }

            dto.setPlaca(radar.getPlaca());
            dto.setPraca(radar.getLocal());
            dto.setSentido(radar.getSentido());
            dto.setConcessionaria(radar.getConcessionaria());

            // 4. Injeta as coordenadas
            if (radar.getLocal() != null) {
                String chaveBusca = radar.getLocal().toUpperCase().replace(" ", "");
                MapaCoordenadasService.Coordenada coord = mapaCoordenadas.getCoordenada(chaveBusca);

                if(coord != null) {
                    dto.setLatitude(coord.latitude);
                    dto.setLongitude(coord.longitude);
                }
            }

            return dto;
        }).collect(Collectors.toList());

        // 5. Retornar dados paginados
        RadarPageDTO response = new RadarPageDTO();
        response.setContent(content);

        PageMetadata meta = new PageMetadata();
        meta.setNumber(page);
        meta.setSize(size);
        meta.setTotalElements(panacheQuery.count());
        meta.setTotalPages(panacheQuery.pageCount());
        response.setPage(meta);

        return response;
    }

    /**
     * Utilitário: Fórmula de Haversine para calcular distância em metros entre duas coordenadas.
     */
    private double calcularDistanciaHaversine(double lat1, double lon1, double lat2, double lon2) {
        final int RAIO_TERRA_KM = 6371;
        double latDist = Math.toRadians(lat2 - lat1);
        double lonDist = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDist / 2) * Math.sin(latDist / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDist / 2) * Math.sin(lonDist / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RAIO_TERRA_KM * c * 1000; // Converte para metros
    }

    /** Tenta múltiplos formatos de data para tolerância a dados heterogêneos. */
    private LocalDate parseData(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try { return LocalDate.parse(raw.trim(), fmt); }
            catch (DateTimeParseException ignored) { /* tenta o próximo */ }
        }
        LOG.warnf("Não foi possível parsear data: '%s'", raw);
        return null;
    }

    /** Tenta múltiplos formatos de hora. */
    private LocalTime parseHora(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (DateTimeFormatter fmt : TIME_FORMATTERS) {
            try { return LocalTime.parse(raw.trim(), fmt); }
            catch (DateTimeParseException ignored) { /* tenta o próximo */ }
        }
        LOG.warnf("Não foi possível parsear hora: '%s'", raw);
        return null;
    }
}
