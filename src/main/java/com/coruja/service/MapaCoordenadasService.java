package com.coruja.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class MapaCoordenadasService {

    private final Map<String, Coordenada> coordenadas = new HashMap<>();

    public static class Coordenada {
        public Double latitude;
        public Double longitude;
    }

    void onStart(@Observes StartupEvent ev) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getClass().getResourceAsStream("/localizacoes-spvias.json");
            Map<String, Coordenada> rawMap = mapper.readValue(is, new TypeReference<Map<String, Coordenada>>(){});

            // Converte chaves para maiúsculo para garantir o JOIN seguro
            rawMap.forEach((k, v) -> coordenadas.put(k.toUpperCase().replace(" ", ""), v));
            log.info("✅ {} localizações da SPVias carregadas.", coordenadas.size());
        } catch (Exception e) {
            log.error("❌ Erro ao carregar localizações: {}", e.getMessage());
        }
    }

    public Coordenada getCoordenada(String km) {
        if (km == null) return null;
        return coordenadas.get(km.toUpperCase().replace(" ", ""));
    }

    // NOVO MÉTODO: Retorna todo o mapa para desenhar os pinos
    public Map<String, Coordenada> getAllCoordenadas() {
        return this.coordenadas;
    }
}
